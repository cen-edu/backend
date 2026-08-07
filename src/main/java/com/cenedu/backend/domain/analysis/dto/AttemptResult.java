package com.cenedu.backend.domain.analysis.dto;

import java.time.Instant;

import com.cenedu.backend.domain.analysis.entity.AttemptPurpose;

/**
 * 학생이 문제 하나를 푼 결과.
 *
 * <p>지금은 문항 하나에 대표 풀이 단계({@code stepId}) 하나만 붙인다.
 */
public record AttemptResult(
        String eventId,
        String learnerId,
        String problemId,
        String conceptId,
        String stepId,
        boolean correct,
        boolean hintUsed,
        Instant occurredAt,
        AttemptPurpose purpose
) {
    public AttemptResult {
        requireText(eventId, "eventId");
        requireText(learnerId, "learnerId");
        requireText(problemId, "problemId");
        requireText(conceptId, "conceptId");
        requireText(stepId, "stepId");
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt은 필수입니다.");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("purpose는 필수입니다.");
        }
    }

    /** 목적을 밝히지 않은 응답은 진단으로 본다. */
    public AttemptResult(
            String eventId,
            String learnerId,
            String problemId,
            String conceptId,
            String stepId,
            boolean correct,
            boolean hintUsed,
            Instant occurredAt
    ) {
        this(eventId, learnerId, problemId, conceptId, stepId,
                correct, hintUsed, occurredAt, AttemptPurpose.DIAGNOSTIC);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "는 필수입니다.");
        }
    }
}
