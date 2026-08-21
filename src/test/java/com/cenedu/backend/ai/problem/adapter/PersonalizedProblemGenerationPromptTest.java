package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.cenedu.backend.ai.problem.adapter.semantic.ProblemSemanticGenerationPromptFactory;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationDiagnosticEvidence;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationEvaluationAreaEvidence;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.PersonalizedGenerationEvidence;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;

class PersonalizedProblemGenerationPromptTest {

    @Test
    void legacy_prompt에_구조화된_근거가_포함되고_학생_답안_원문은_포함되지_않는다() {
        ProblemGenerationPrompt generated = new ProblemGenerationPromptFactory().create(command());
        String prompt = generated.messages().getLast().content();

        assertThat(prompt).contains("personalizedEvidence", "historicalIncorrectItemCount",
                "evaluationAreaEvidence", "diagnosticEvidence", "EXECUTE");
        assertThat(prompt).doesNotContain("studentAnswer", "handwritingText");
        assertThat(generated.systemPrompt()).contains("$...$", "answerRaw");
    }

    @Test
    void semantic_prompt에_구조화된_근거가_포함된다() {
        String prompt = new ProblemSemanticGenerationPromptFactory(new FewShotReferenceSerializer())
                .create(command(), List.of());

        assertThat(prompt).contains("personalizedEvidence", "historicalIncorrectItemCount",
                "incorrectRate", "EXECUTE", "$...$");
    }

    private ProblemGenerationCommand command() {
        PersonalizedGenerationEvidence evidence = new PersonalizedGenerationEvidence(
                8, 3,
                List.of(new GenerationEvaluationAreaEvidence(
                        EvaluationArea.CALCULATION, 6, 4, BigDecimal.valueOf(66.67))),
                List.of(new GenerationDiagnosticEvidence(
                        DiagnosticType.EXECUTE, 8, 5, BigDecimal.valueOf(62.5))));
        return new ProblemGenerationCommand(UUID.randomUUID(), null,
                GenerationPurpose.PERSONALIZED_APPLICATION,
                new GenerationSpecification(QuestionType.STEP_FILL, "high",
                        EvaluationArea.CALCULATION, List.of(DiagnosticType.EXECUTE), true),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 14L,
                        "수와 연산", "식의 계산", "소인수분해"),
                List.of(), List.of(), evidence);
    }
}
