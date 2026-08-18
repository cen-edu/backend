package com.cenedu.backend.domain.problem.authoring.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.UUID;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import org.junit.jupiter.api.Test;

class ProblemGenerationPlanTest {
    @Test
    void 은행과_AI_슬롯은_화면순서를_보존한다() {
        ProblemGenerationCommand command = new ProblemGenerationCommand(UUID.randomUUID(),
            GenerationPurpose.PERSONALIZED_APPLICATION, null, null, List.of(), List.of());
        ProblemGenerationPlan plan = new ProblemGenerationPlan(UUID.randomUUID(),
            GenerationJobType.PERSONALIZED, List.of(
                new ProblemGenerationSlotPlan(1, GenerationSlotSource.BANK_REUSE, 30L,
                        new com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1(
                                1, new com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata(
                                com.cenedu.backend.global.common.enums.QuestionType.SHORT_INPUT,
                                com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation.TEXT_ONLY,
                                "mid", 1L, null, null, null), List.of(), List.of(), List.of(),
                                List.of(), List.of(), null, null, List.of()), null),
                new ProblemGenerationSlotPlan(2, GenerationSlotSource.AI_GENERATION, null, command)));

        assertThat(plan.slots()).extracting(ProblemGenerationSlotPlan::source)
            .containsExactly(GenerationSlotSource.BANK_REUSE, GenerationSlotSource.AI_GENERATION);
    }

    @Test
    void 슬롯의_공급원과_payload가_섞이면_거절한다() {
        assertThatThrownBy(() -> new ProblemGenerationSlotPlan(1,
            GenerationSlotSource.BANK_REUSE, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
