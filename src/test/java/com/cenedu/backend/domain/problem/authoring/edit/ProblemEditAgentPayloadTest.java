package com.cenedu.backend.domain.problem.authoring.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import org.junit.jupiter.api.Test;

class ProblemEditAgentPayloadTest {

    private final QuestionSnapshotV1 snapshot = new QuestionSnapshotV1(
            1, null, List.of(), List.of(), List.of(), List.of(), List.of(),
            null, null, List.of());

    @Test
    void 문제_수정_payload는_구조화된_지시목록을_복사한다() {
        ProblemEditInstruction instruction = new ProblemEditInstruction(
                EditTargetType.EXPLANATION,
                null,
                EditChangeNature.PRESENTATIONAL,
                "해설 표현을 간결하게 바꿔 주세요.");

        ProblemEditAgentPayload payload = new ProblemEditAgentPayload(
                1,
                10L,
                20L,
                AuthoringInteractionStatus.COLLECTING,
                null,
                snapshot,
                List.of(instruction));

        assertThat(payload.accumulatedInstructions()).containsExactly(instruction);
        assertThat(payload.accumulatedInstructions()).isUnmodifiable();
    }

    @Test
    void 지원하지_않는_payload_버전은_거절한다() {
        assertThatThrownBy(() -> new ProblemEditAgentPayload(
                2, 10L, 20L, AuthoringInteractionStatus.IDLE,
                null, snapshot, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
