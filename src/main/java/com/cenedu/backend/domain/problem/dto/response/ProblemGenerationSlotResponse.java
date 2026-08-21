package com.cenedu.backend.domain.problem.dto.response;

/** 생성 Job 안에서 화면 순서를 고정하는 문항 하나의 진행 상태와 미리보기다. */
public record ProblemGenerationSlotResponse(
        int slotIndex,
        Long itemId,
        Long sessionId,
        String customStage,
        Long sourceQuestionId,
        Long originQuestionId,
        AuthoringSlotDisplayStatus status,
        AuthoringProblemSnapshotResponse preview,
        String errorCode,
        boolean retryable
) {

    public ProblemGenerationSlotResponse(int slotIndex, Long itemId, Long sessionId,
                                         AuthoringSlotDisplayStatus status,
                                         AuthoringProblemSnapshotResponse preview,
                                         String errorCode, boolean retryable) {
        this(slotIndex, itemId, sessionId, null, null, null, status, preview, errorCode, retryable);
    }

    public ProblemGenerationSlotResponse {
        if (slotIndex < 1) {
            throw new IllegalArgumentException("slotIndex는 1 이상이어야 합니다.");
        }
        requirePositive(itemId, "itemId");
        requirePositive(sessionId, "sessionId");
        if (status == null) {
            throw new IllegalArgumentException("status는 필수입니다.");
        }
        if (status == AuthoringSlotDisplayStatus.READY && preview == null) {
            throw new IllegalArgumentException("READY 문항은 미리보기가 필요합니다.");
        }
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "은 1 이상이어야 합니다.");
        }
    }
}
