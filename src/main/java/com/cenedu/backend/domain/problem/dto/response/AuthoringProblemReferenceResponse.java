package com.cenedu.backend.domain.problem.dto.response;

/** 기존 questionId와 작성 sessionId를 한 필드에 섞지 않는 문항 후보 참조다. */
public record AuthoringProblemReferenceResponse(
        ProblemReferenceType type,
        Long questionId,
        Long sessionId
) {

    public AuthoringProblemReferenceResponse {
        boolean existing = type == ProblemReferenceType.EXISTING_QUESTION
                && questionId != null && sessionId == null;
        boolean authoring = type == ProblemReferenceType.AUTHORING_SESSION
                && questionId == null && sessionId != null;
        if (!existing && !authoring) {
            throw new IllegalArgumentException(
                    "기존 문항은 questionId만, 작성 후보는 sessionId만 필요합니다.");
        }
        if ((questionId != null && questionId <= 0)
                || (sessionId != null && sessionId <= 0)) {
            throw new IllegalArgumentException("questionId와 sessionId는 양수여야 합니다.");
        }
    }

    /** 문제은행에 이미 있는 문항 참조를 만든다. */
    public static AuthoringProblemReferenceResponse existingQuestion(long questionId) {
        return new AuthoringProblemReferenceResponse(
                ProblemReferenceType.EXISTING_QUESTION, questionId, null);
    }

    /** 생성·수정·검증 중인 Session 참조를 만든다. */
    public static AuthoringProblemReferenceResponse authoringSession(long sessionId) {
        return new AuthoringProblemReferenceResponse(
                ProblemReferenceType.AUTHORING_SESSION, null, sessionId);
    }
}
