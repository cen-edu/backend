package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.ai.problem.ProblemStructuredOutputSchemas;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotNormalizedValidator;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProblemModificationAdapterTest {
    @Test
    void 해설_수정은_explanation만_Delta로_허용한다() {
        String schema = ProblemStructuredOutputSchemas.modificationDeltaFor(Set.of(EditTargetType.EXPLANATION));
        assertThat(schema).contains("explanation").doesNotContain("question", "answerUnits", "choices");
    }

    @Test
    void 문제본문_수정은_본문필드만_허용한다() {
        String schema = ProblemStructuredOutputSchemas.modificationDeltaFor(Set.of(EditTargetType.QUESTION_BODY));
        assertThat(schema).contains("question", "contentBlocks").doesNotContain("explanation", "answerUnits");
    }

    @Test
    @SuppressWarnings("unchecked")
    void REPLACE_action은_정답을_포함한_모델_출력을_기준_Snapshot으로_되돌리지_않는다() {
        LlmClient client = mock(LlmClient.class);
        when(client.completeStructured(any(), any(), any())).thenReturn(new LlmResponse("""
                {"question":"24를 구하시오.","explanation":"식을 계산하면 24이다.",
                 "learningGuide":{"conceptTitle":"사칙연산","summary":"사칙연산을 사용해 문제를 해결한다.","keyPoints":["연산 순서를 확인한다."]},
                 "answerUnits":[{"answerRaw":"24","compareMethod":"VALUE"}]}
                """, 1, 1, 0));
        ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> mapper = mock(ObjectProvider.class);
        when(mapper.getIfAvailable(any())).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        SnapshotStructuralValidator structural = new SnapshotStructuralValidator();
        var adapter = new ProblemModificationAdapter(client, mapper,
                new ModificationPromptStrategy(new tools.jackson.databind.ObjectMapper()),
                new ProblemGenerationOutputMapper(), structural,
                new SnapshotNormalizedValidator(structural), new ProblemModificationSnapshotMerger());

        var base = ProblemSnapshotFixtures.shortInput();
        var plan = new ProblemEditExecutionPlan(UUID.randomUUID(), 1L, 2L,
                EditAction.REPLACE, ReplacementSourcePolicy.NONE, null,
                List.of(new ProblemEditInstruction(EditTargetType.WHOLE_QUESTION, null,
                        EditChangeNature.SEMANTIC, "값을 24로 바꾼다")),
                null, List.of(new ProblemEditTargetRef(EditTargetType.WHOLE_QUESTION, null)),
                List.of(), List.of(), null);

        var candidate = adapter.modify(new ProblemModificationCommand(plan.requestId(), plan, base));

        assertThat(candidate.snapshot().answerUnits().getFirst().answerRaw()).isEqualTo("24");
        assertThat(candidate.snapshot().explanation()).isEqualTo("식을 계산하면 24이다.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void MODIFY_action은_요청받지_않은_정답을_기준_Snapshot으로_보존한다() {
        LlmClient client = mock(LlmClient.class);
        when(client.completeStructured(any(), any(), any())).thenReturn(new LlmResponse("""
                {"question":"12를 구하시오.",
                 "explanation":"더 자세히 계산 과정을 설명한다.",
                 "learningGuide":{"conceptTitle":"사칙연산","summary":"사칙연산을 사용해 문제를 해결한다.","keyPoints":["연산 순서를 확인한다."]},
                 "answerUnits":[{"answerRaw":"999","compareMethod":"VALUE"}]}
                """, 1, 1, 0));
        ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> mapper = mock(ObjectProvider.class);
        when(mapper.getIfAvailable(any())).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        SnapshotStructuralValidator structural = new SnapshotStructuralValidator();
        var adapter = new ProblemModificationAdapter(client, mapper,
                new ModificationPromptStrategy(new tools.jackson.databind.ObjectMapper()),
                new ProblemGenerationOutputMapper(), structural,
                new SnapshotNormalizedValidator(structural), new ProblemModificationSnapshotMerger());

        var base = ProblemSnapshotFixtures.shortInput();
        var plan = new ProblemEditExecutionPlan(UUID.randomUUID(), 1L, 2L,
                EditAction.MODIFY, ReplacementSourcePolicy.NONE, null,
                List.of(new ProblemEditInstruction(EditTargetType.EXPLANATION, null,
                        EditChangeNature.PRESENTATIONAL, "해설을 더 자세히")),
                null, List.of(new ProblemEditTargetRef(EditTargetType.EXPLANATION, null)),
                List.of(), List.of(new ProblemEditTargetRef(EditTargetType.ANSWER_UNIT, "MAIN")), null);

        var candidate = adapter.modify(new ProblemModificationCommand(plan.requestId(), plan, base));

        assertThat(candidate.snapshot().answerUnits().getFirst().answerRaw()).isEqualTo("12");
        assertThat(candidate.snapshot().explanation()).isEqualTo("더 자세히 계산 과정을 설명한다.");
    }
}
