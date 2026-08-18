package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.dto.response.AuthoringProblemSnapshotResponse;
import com.cenedu.backend.domain.problem.dto.response.AuthoringSessionStatusResponse;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringLifecycleStatus;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationStatus;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringVerificationStatus;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 화면·Worksheet·Grading이 Problem Entity 대신 검증된 S1과 Session 상태를 조회하는 공개 경계다.
 *
 * <p><b>배세빈 팀원 연동 범위:</b> Worksheet는 Repository를 직접 조회하지 않고
 * {@link #getStatus(long, long)}의 {@code readyForFinalization}으로 문항 완료 가능 여부를 확인한다.
 * 최종 저장된 문항의 채점·검증 입력이 필요하면 {@link #getFinalized(long)}로 저장 당시 S1을
 * 조회한다. Worksheet·Grading 전용 필드가 더 필요하면 Entity 참조를 추가하지 않고 Problem
 * 도메인의 Response 계약을 이하영과 먼저 확장한다.
 */
@Service
@RequiredArgsConstructor
public class ProblemSnapshotQueryService {

    private final ProblemAuthoringSessionRepository sessionRepository;
    private final ProblemAuthoringVersionRepository versionRepository;
    private final ProblemAuthoringJsonCodec jsonCodec;

    /** 교사 미리보기에 보여줄 Session의 현재 PASSED S1을 반환한다. */
    @Transactional(readOnly = true)
    public AuthoringProblemSnapshotResponse getCurrent(long ownerTeacherId,
                                                       long sessionId) {
        ProblemAuthoringSession session = sessionRepository
                .findByIdAndOwnerTeacherId(sessionId, ownerTeacherId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
        return snapshotResponse(session);
    }

    /** Session의 생성·수정·검증 진행과 최종화 가능 여부를 반환한다. */
    @Transactional(readOnly = true)
    public AuthoringSessionStatusResponse getStatus(long ownerTeacherId,
                                                    long sessionId) {
        ProblemAuthoringSession session = sessionRepository
                .findByIdAndOwnerTeacherId(sessionId, ownerTeacherId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
        return new AuthoringSessionStatusResponse(
                session.getId(),
                session.getLifecycleStatus(),
                session.getOperationStatus(),
                session.getInteractionStatus(),
                session.getCurrentVersionId(),
                session.getPendingVersionId(),
                readyForFinalization(session),
                session.getFinalizedQuestionId(),
                session.getLastErrorCode());
    }

    /** 최종 questionId로 저장 당시의 PASSED S1을 채점·검증에 반환한다. */
    @Transactional(readOnly = true)
    public AuthoringProblemSnapshotResponse getFinalized(long questionId) {
        ProblemAuthoringSession session = sessionRepository
                .findByFinalizedQuestionId(questionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
        if (session.getLifecycleStatus() != AuthoringLifecycleStatus.FINALIZED) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_VERIFIED);
        }
        return snapshotResponse(session);
    }

    private AuthoringProblemSnapshotResponse snapshotResponse(
            ProblemAuthoringSession session) {
        if (session.getCurrentVersionId() == null) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_VERIFIED);
        }
        ProblemAuthoringVersion version = versionRepository
                .findByIdAndSessionId(
                        session.getCurrentVersionId(), session.getId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
        if (version.getVerificationStatus() != AuthoringVerificationStatus.PASSED) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_VERIFIED);
        }
        return new AuthoringProblemSnapshotResponse(
                session.getId(), version.getId(), session.getFinalizedQuestionId(),
                jsonCodec.read(version.getSnapshot(), QuestionSnapshotV1.class));
    }

    private boolean readyForFinalization(ProblemAuthoringSession session) {
        if (session.getLifecycleStatus() != AuthoringLifecycleStatus.DRAFT
                || session.getOperationStatus() != AuthoringOperationStatus.IDLE
                || session.getInteractionStatus() != AuthoringInteractionStatus.IDLE
                || session.getCurrentVersionId() == null
                || session.getPendingVersionId() != null) {
            return false;
        }
        return versionRepository.findByIdAndSessionId(
                        session.getCurrentVersionId(), session.getId())
                .map(version -> version.getVerificationStatus()
                        == AuthoringVerificationStatus.PASSED)
                .orElse(false);
    }
}
