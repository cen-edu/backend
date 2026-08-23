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
    void WHOLE_QUESTION_수정은_빈_스키마가_아니라_CANDIDATE_전체_계약을_쓴다() {
        String schema = ProblemStructuredOutputSchemas.modificationDeltaFor(Set.of(EditTargetType.WHOLE_QUESTION));
        assertThat(schema).isEqualTo(ProblemStructuredOutputSchemas.CANDIDATE)
                .contains("\"required\"")
                .contains("learningGuide", "answerUnits", "choices", "explanation");
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
    void REPLACE_action도_이미지_자산은_기준_Snapshot에서_그대로_가져온다() {
        // CANDIDATE 스키마는 assets를 항상 빈 배열로 강제하므로, 레거시 경로는 이 필드를
        // 모델 출력에서 절대 채울 수 없다. REPLACE라고 해서 기존 이미지 참조를 잃으면 안 된다.
        LlmClient client = mock(LlmClient.class);
        when(client.completeStructured(any(), any(), any())).thenReturn(new LlmResponse("""
                {"question":"다음 그래프가 나타내는 관계식으로 알맞은 것을 고르시오.",
                 "contentBlocks":[{"blockKind":"TEXT","text":"다음 그래프가 나타내는 관계식으로 알맞은 것을 고르시오.","assetRef":null,"markup":null},
                                   {"blockKind":"FIGURE","text":null,"assetRef":"F1","markup":null}],
                 "choices":[{"content":"$y=3x$"},{"content":"$y=2x$"}],
                 "explanation":"비례상수는 3이다.",
                 "learningGuide":{"conceptTitle":"정비례","summary":"정비례 관계","keyPoints":["비를 확인한다"]},
                 "answerUnits":[{"answerRaw":"C1","compareMethod":"CHOICE"}]}
                """, 1, 1, 0));
        ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> mapper = mock(ObjectProvider.class);
        when(mapper.getIfAvailable(any())).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        SnapshotStructuralValidator structural = new SnapshotStructuralValidator();
        var adapter = new ProblemModificationAdapter(client, mapper,
                new ModificationPromptStrategy(new tools.jackson.databind.ObjectMapper()),
                new ProblemGenerationOutputMapper(), structural,
                new SnapshotNormalizedValidator(structural), new ProblemModificationSnapshotMerger());

        var base = withFigureAsset(ProblemSnapshotFixtures.shortInput());
        var plan = new ProblemEditExecutionPlan(UUID.randomUUID(), 1L, 2L,
                EditAction.REPLACE, ReplacementSourcePolicy.NONE, null,
                List.of(new ProblemEditInstruction(EditTargetType.WHOLE_QUESTION, null,
                        EditChangeNature.SEMANTIC, "그래프의 기울기를 2에서 3으로")),
                null, List.of(new ProblemEditTargetRef(EditTargetType.WHOLE_QUESTION, null)),
                List.of(), List.of(), null);

        var candidate = adapter.modify(new ProblemModificationCommand(plan.requestId(), plan, base));

        assertThat(candidate.snapshot().assets()).hasSize(1);
        assertThat(candidate.snapshot().assets().getFirst().altText()).isEqualTo("원래 그래프 설명");
        assertThat(candidate.snapshot().answerUnits().getFirst().answerRaw()).isEqualTo("C1");
    }

    private com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 withFigureAsset(
            com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 base) {
        var asset = new com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference(
                "F1", "원래 그래프 설명");
        var figureBlock = new com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock(
                "CB2", com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind.FIGURE,
                1, null, "F1", null);
        var blocks = new java.util.ArrayList<>(base.contentBlocks());
        blocks.add(figureBlock);
        // 실제 재현 시나리오(그래프 이미지가 있는 객관식 문항)와 맞춰 MULTIPLE_CHOICE로 바꾼다.
        var metadata = new com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata(
                com.cenedu.backend.global.common.enums.QuestionType.MULTIPLE_CHOICE,
                com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation.WITH_FIGURE,
                base.metadata().difficulty(), base.metadata().subUnitId(), base.metadata().topicCode(),
                base.metadata().evaluationArea(), base.metadata().derivedFromQuestionId());
        var choices = List.of(new com.cenedu.backend.domain.problem.authoring.model.SnapshotChoice(
                "C1", 0, "$y=2x$"));
        var answerUnits = List.of(new com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit(
                "MAIN", null, 0, "C1", null,
                com.cenedu.backend.global.common.enums.CompareMethod.CHOICE, null, null));
        return new com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1(
                base.schemaVersion(), metadata, List.copyOf(blocks), List.of(asset),
                choices, base.steps(), answerUnits, base.explanation(),
                base.learningGuide(), base.rubricItems());
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
