package com.cenedu.backend.domain.problem.authoring.edit;

import java.util.List;
import java.util.Objects;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;

/** PROBLEM_EDIT Agent에 전달하는 문제 전용 컨텍스트다. Dispatcher는 내부 필드를 해석하지 않는다. */
public record ProblemEditAgentPayload(
        int schemaVersion,
        Long sessionId,
        Long baseVersionId,
        AuthoringInteractionStatus interactionStatus,
        ProblemEditTargetRef selectedTarget,
        QuestionSnapshotV1 currentSnapshot,
        List<ProblemEditInstruction> accumulatedInstructions
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ProblemEditAgentPayload {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("지원하지 않는 문제 수정 payload 버전입니다.");
        }
        requirePositive(sessionId, "sessionId");
        requirePositive(baseVersionId, "baseVersionId");
        Objects.requireNonNull(interactionStatus, "interactionStatus");
        Objects.requireNonNull(currentSnapshot, "currentSnapshot");
        accumulatedInstructions = accumulatedInstructions == null
                ? List.of()
                : List.copyOf(accumulatedInstructions);
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "은 1 이상이어야 합니다.");
        }
    }
}
