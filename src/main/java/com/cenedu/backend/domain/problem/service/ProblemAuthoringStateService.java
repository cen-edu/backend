package com.cenedu.backend.domain.problem.service;

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

/** Session·Version 포인터를 검증 결과와 함께 안전하게 전이시킨다. */
@Service
@RequiredArgsConstructor
public class ProblemAuthoringStateService {

    private final ProblemAuthoringSessionRepository sessionRepository;
    private final ProblemAuthoringVersionRepository versionRepository;

    /** 의미 검증을 통과한 pending Version만 교사에게 보여줄 current로 승격한다. */
    @Transactional
    public void promotePassedVersion(long ownerTeacherId, long sessionId, long versionId) {
        ProblemAuthoringSession session = getOwnedSession(sessionId, ownerTeacherId);
        ProblemAuthoringVersion version = getSessionVersion(versionId, sessionId);
        requirePassed(version);
        session.promotePendingVersion(versionId, version.getVerificationStatus());
    }

    /** 검증 실패 후보를 이력으로는 남기고 current에는 반영하지 않는다. */
    @Transactional
    public void rejectFailedVersion(long ownerTeacherId, long sessionId, long versionId,
                                    String errorCode) {
        ProblemAuthoringSession session = getOwnedSession(sessionId, ownerTeacherId);
        ProblemAuthoringVersion version = getSessionVersion(versionId, sessionId);
        if (version.getVerificationStatus() != AuthoringVerificationStatus.FAILED) {
            throw new IllegalStateException("FAILED Version만 실패 후보로 처리할 수 있습니다.");
        }
        session.failPendingVersion(versionId, errorCode);
    }

    /** 같은 Session의 이전 PASSED Version을 현재 표시 문항으로 복원한다. */
    @Transactional
    public void restorePassedVersion(long ownerTeacherId, long sessionId, long versionId) {
        ProblemAuthoringSession session = getOwnedSession(sessionId, ownerTeacherId);
        ProblemAuthoringVersion version = getSessionVersion(versionId, sessionId);
        requirePassed(version);
        session.restorePassedVersion(versionId, version.getVerificationStatus());
    }

    /** 현재 Version이 PASSED임을 재확인한 후 문제은행 questionId와 Session을 연결한다. */
    @Transactional
    public void finalizeSession(long ownerTeacherId, long sessionId, long questionId) {
        ProblemAuthoringSession session = sessionRepository
                .findOwnedByIdForUpdate(sessionId, ownerTeacherId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
        Long currentVersionId = session.getCurrentVersionId();
        if (currentVersionId == null) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_VERIFIED);
        }
        ProblemAuthoringVersion version = getSessionVersion(currentVersionId, sessionId);
        requirePassed(version);
        session.finalizeAs(questionId, version.getVerificationStatus());
    }

    private ProblemAuthoringSession getOwnedSession(long sessionId, long ownerTeacherId) {
        return sessionRepository.findByIdAndOwnerTeacherId(sessionId, ownerTeacherId)
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
