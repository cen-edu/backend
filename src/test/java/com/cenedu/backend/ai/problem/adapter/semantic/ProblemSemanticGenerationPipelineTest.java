package com.cenedu.backend.ai.problem.adapter.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import org.junit.jupiter.api.Test;

class ProblemSemanticGenerationPipelineTest {
    @Test void repairFindingsAreBoundedAndPromptKeepsRequestContext() {
        var command = new ProblemGenerationCommand(java.util.UUID.randomUUID(), null, GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(com.cenedu.backend.global.common.enums.QuestionType.SHORT_INPUT, "mid", null, java.util.List.of()),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L, "major", "middle", "sub"), java.util.List.of(), java.util.List.of());
        var findings = java.util.stream.IntStream.range(0, 20).mapToObj(i -> "F".repeat(300)).toList();
        String prompt = new ProblemSemanticGenerationPromptFactory().create(command, findings);
        assertThat(prompt).contains("CURRENT_REQUEST_JSON").contains("REPAIR_FINDINGS");
        assertThat(prompt.indexOf("F".repeat(201))).isNegative();
    }
}
