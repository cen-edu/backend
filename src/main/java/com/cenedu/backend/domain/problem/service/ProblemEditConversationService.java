package com.cenedu.backend.domain.problem.service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.cenedu.backend.domain.problem.authoring.edit.ConfirmedProblemEditCommand;
import com.cenedu.backend.domain.problem.authoring.edit.EditAction;
import com.cenedu.backend.domain.problem.authoring.edit.PendingProblemEditCommand;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import com.cenedu.backend.domain.problem.authoring.edit.RestoreReference;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringVerificationStatus;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dispatcher 전·후의 HITL 상태를 관리하고 교사 확인 명령만 실행 계획으로 고정한다. */
@Service
@RequiredArgsConstructor
public class ProblemEditConversationService {

    private static final int EDIT_SCHEMA_VERSION = 1;

    private final ProblemAuthoringSessionRepository sessionRepository;
    private final ProblemAuthoringVersionRepository versionRepository;
    private final ProblemAuthoringJsonCodec jsonCodec;
    private final ProblemEditPolicy editPolicy;

    /** current PASSED Version이 있는 Session에서 수정 요청 수집을 시작한다. */
    @Transactional
    public void start(long ownerTeacherId, long sessionId) {
        ProblemAuthoringSession session = getOwnedSessionForUpdate(
                sessionId, ownerTeacherId);
        if (session.getCurrentVersionId() == null) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_VERIFIED);
        }
        session.startCollecting();
    }

    /** 누적된 구조화 수정 사항을 저장하고 교사의 최종 확인을 기다린다. */
    @Transactional
    public void requestConfirmation(long ownerTeacherId,
                                    PendingProblemEditCommand pendingCommand) {
        requirePendingCommand(pendingCommand);
        ProblemAuthoringSession session = getOwnedSessionForUpdate(
                pendingCommand.sessionId(), ownerTeacherId);
        requireCurrentBase(session, pendingCommand.baseVersionId());
        session.awaitConfirmation(
                jsonCodec.write(pendingCommand), EDIT_SCHEMA_VERSION);
    }

    /** 교사가 확인한 명령을 비교한 후 복원하거나 불변 실행 계획으로 활성화한다. */
    @Transactional
    public ProblemEditExecutionPlan confirm(long ownerTeacherId,
                                            ConfirmedProblemEditCommand confirmedCommand) {
        if (confirmedCommand == null || confirmedCommand.confirmationMessageId() == null) {
            throw new IllegalArgumentException("교사 확인 메시지 ID가 필요합니다.");
        }
        ProblemAuthoringSession session = getOwnedSessionForUpdate(
                confirmedCommand.sessionId(), ownerTeacherId);
        requireCurrentBase(session, confirmedCommand.baseVersionId());
        if (session.getPendingInstructions() == null) {
            throw new BusinessException(ErrorCode.PROBLEM_EDIT_COMMAND_STALE);
        }
        PendingProblemEditCommand pending = jsonCodec.read(
                session.getPendingInstructions(), PendingProblemEditCommand.class);
        requireSameCommand(pending, confirmedCommand);

        ProblemAuthoringVersion baseVersion = getSessionVersion(
                confirmedCommand.baseVersionId(), confirmedCommand.sessionId());
        requirePassed(baseVersion);
        QuestionSnapshotV1 baseSnapshot = jsonCodec.read(
                baseVersion.getSnapshot(), QuestionSnapshotV1.class);
        ProblemAuthoringVersion restoreVersion = resolveRestoreVersion(
                confirmedCommand, baseVersion);
        ProblemEditExecutionPlan plan = editPolicy.plan(
                confirmedCommand,
                baseSnapshot,
                restoreVersion == null ? null : restoreVersion.getId());

        if (plan.action() == EditAction.RESTORE) {
            session.completeConfirmedRestore(
                    restoreVersion.getId(), restoreVersion.getVerificationStatus());
        } else {
            session.activateEdit(
                    plan.requestId(), plan.baseVersionId(),
                    jsonCodec.write(plan), EDIT_SCHEMA_VERSION);
        }
        return plan;
    }

    /** 실행 전 수집·확인 대기 상태를 취소한다. */
    @Transactional
    public void cancel(long ownerTeacherId, long sessionId) {
        getOwnedSessionForUpdate(sessionId, ownerTeacherId).cancelInteraction();
    }

    private ProblemAuthoringVersion resolveRestoreVersion(
            ConfirmedProblemEditCommand command,
            ProblemAuthoringVersion baseVersion
    ) {
        RestoreReference reference = command.restoreReference();
        if (reference == null) {
            return null;
        }
        List<ProblemAuthoringVersion> passedVersions = versionRepository
                .findAllBySessionIdOrderByVersionNo(command.sessionId()).stream()
                .filter(version -> version.getVerificationStatus()
                        == AuthoringVerificationStatus.PASSED)
                .toList();
        return switch (reference.type()) {
            case FIRST -> passedVersions.stream()
                    .min(Comparator.comparingInt(ProblemAuthoringVersion::getVersionNo))
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
            case PREVIOUS -> passedVersions.stream()
                    .filter(version -> version.getVersionNo() < baseVersion.getVersionNo())
                    .max(Comparator.comparingInt(ProblemAuthoringVersion::getVersionNo))
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
            case VERSION_NO -> {
                if (reference.versionNo() == null) {
                    throw new IllegalArgumentException("복원할 Version 번호가 필요합니다.");
                }
                ProblemAuthoringVersion version = versionRepository
                        .findBySessionIdAndVersionNo(
                                command.sessionId(), reference.versionNo())
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
                requirePassed(version);
                yield version;
            }
        };
    }

    private void requireSameCommand(PendingProblemEditCommand pending,
                                    ConfirmedProblemEditCommand confirmed) {
        boolean same = pending != null
                && Objects.equals(pending.requestId(), confirmed.requestId())
                && Objects.equals(pending.sessionId(), confirmed.sessionId())
                && Objects.equals(pending.baseVersionId(), confirmed.baseVersionId())
                && Objects.equals(pending.instructions(), confirmed.instructions())
                && Objects.equals(pending.requestedSpecification(),
                        confirmed.requestedSpecification())
                && Objects.equals(pending.restoreReference(), confirmed.restoreReference())
                && pending.replacementSourcePolicy()
                        == confirmed.replacementSourcePolicy();
        if (!same) {
            throw new BusinessException(ErrorCode.PROBLEM_EDIT_COMMAND_STALE);
        }
    }

    private void requirePendingCommand(PendingProblemEditCommand command) {
        if (command == null || command.requestId() == null
                || command.sessionId() == null || command.baseVersionId() == null
                || command.replacementSourcePolicy() == null) {
            throw new IllegalArgumentException("수정 확인 대기 명령 필수값이 누락되었습니다.");
        }
    }

    private void requireCurrentBase(ProblemAuthoringSession session,
                                    Long baseVersionId) {
        if (!Objects.equals(session.getCurrentVersionId(), baseVersionId)) {
            throw new BusinessException(ErrorCode.PROBLEM_EDIT_COMMAND_STALE);
        }
    }

    private ProblemAuthoringSession getOwnedSessionForUpdate(long sessionId,
                                                              long ownerTeacherId) {
        return sessionRepository.findOwnedByIdForUpdate(sessionId, ownerTeacherId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
    }

    private ProblemAuthoringVersion getSessionVersion(long versionId, long sessionId) {
        return versionRepository.findByIdAndSessionId(versionId, sessionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
    }

    private void requirePassed(ProblemAuthoringVersion version) {
        if (version.getVerificationStatus() != AuthoringVerificationStatus.PASSED) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_VERIFIED);
        }
    }
}
