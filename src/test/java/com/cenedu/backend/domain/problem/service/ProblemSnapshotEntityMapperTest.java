package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.List;

import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import com.cenedu.backend.domain.problem.authoring.semantic.persistence.SemanticModelDocument;

class ProblemSnapshotEntityMapperTest {
    @Test
    void S1을_문제은행_본체와_정답_단위로_변환한다() {
        ProblemQuestionPersistenceBundle bundle = new ProblemSnapshotEntityMapper(new ObjectMapper())
                .map(ProblemSnapshotFixtures.shortInput(), Map.of());

        assertThat(bundle.question().getQuestionType()).isEqualTo(
                com.cenedu.backend.global.common.enums.QuestionType.SHORT_INPUT);
        assertThat(bundle.question().getDifficulty()).isEqualTo((short) 2);
        assertThat(bundle.answerUnits()).hasSize(1);
        assertThat(bundle.answerUnits().getFirst().getAnswerNormalized()).isEqualTo("12");
        assertThat(bundle.choices()).isEmpty();
    }

    @Test
    void 객관식_논리키를_기존_채점계약의_일기준_번호로_저장한다() {
        var base = ProblemSnapshotFixtures.shortInput();
        var snapshot = new com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1(
                1, new com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata(
                com.cenedu.backend.global.common.enums.QuestionType.MULTIPLE_CHOICE,
                com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation.TEXT_ONLY,
                "mid", 1L, null, null, null), base.contentBlocks(), List.of(),
                List.of(new com.cenedu.backend.domain.problem.authoring.model.SnapshotChoice("C1", 0, "10"),
                        new com.cenedu.backend.domain.problem.authoring.model.SnapshotChoice("C2", 1, "12")),
                List.of(), List.of(new com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit(
                "MAIN", null, 0, "C2", null,
                com.cenedu.backend.global.common.enums.CompareMethod.CHOICE, null, null)),
                base.explanation(), base.learningGuide(), List.of());

        ProblemQuestionPersistenceBundle bundle = new ProblemSnapshotEntityMapper(new ObjectMapper())
                .map(snapshot, Map.of());

        assertThat(bundle.answerUnits().getFirst().getAnswerRaw()).isEqualTo("2");
    }

    @Test
    void 평가영역과_파생원문을_최종_문항에_보존한다() {
        var base = ProblemSnapshotFixtures.shortInput();
        var metadata = new com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata(
                base.metadata().questionType(), base.metadata().presentation(), base.metadata().difficulty(),
                base.metadata().subUnitId(), "TOPIC", com.cenedu.backend.global.common.enums.EvaluationArea.CALCULATION, 99L);
        var snapshot = new com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1(
                1, metadata, base.contentBlocks(), base.assets(), base.choices(), base.steps(),
                base.answerUnits(), base.explanation(), base.learningGuide(), base.rubricItems());
        var derived = org.mockito.Mockito.mock(com.cenedu.backend.domain.problem.entity.ProblemQuestion.class);

        var bundle = new ProblemSnapshotEntityMapper(new ObjectMapper()).map(snapshot, Map.of(), derived);

        assertThat(bundle.question().getEvaluationArea()).isEqualTo(
                com.cenedu.backend.global.common.enums.EvaluationArea.CALCULATION);
        assertThat(bundle.question().getDerivedFrom()).isSameAs(derived);
    }

    @Test
    void semantic_model_document를_문제에_연결한다() {
        var semantic = new SemanticModelDocument(1, "{\"schemaVersion\":1}", "a".repeat(64));

        var bundle = new ProblemSnapshotEntityMapper(new ObjectMapper()).map(
                ProblemSnapshotFixtures.shortInput(), Map.of(), null, semantic, Map.of());

        assertThat(bundle.question().getSemanticModelStatus())
                .isEqualTo(com.cenedu.backend.domain.problem.entity.enums.SemanticModelStatus.READY);
        assertThat(bundle.question().getSemanticModelHash()).isEqualTo("a".repeat(64));
    }

    @Test
    void render_spec_document를_해당_자산에_연결한다() {
        var base = ProblemSnapshotFixtures.shortInput();
        var snapshot = new com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1(
                1, base.metadata(),
                List.of(new com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock(
                        "CB2", com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind.FIGURE,
                        1, null, "FIGURE_1", null)),
                List.of(new com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference(
                        "FIGURE_1", "도형")), base.choices(), base.steps(), base.answerUnits(),
                base.explanation(), base.learningGuide(), base.rubricItems());
        var render = new com.cenedu.backend.domain.problem.authoring.semantic.persistence.RenderSpecDocument(
                1, "{\"kind\":\"PLANE_GEOMETRY\"}", "b".repeat(64), "semantic-svg-v1");

        var bundle = new ProblemSnapshotEntityMapper(new ObjectMapper()).map(
                snapshot, Map.of("FIGURE_1", "final/figure.svg"), null, null,
                Map.of("FIGURE_1", render));

        assertThat(bundle.assets()).hasSize(1);
        assertThat(bundle.assets().getFirst().getRenderSpecHash()).isEqualTo("b".repeat(64));
        assertThat(bundle.assets().getFirst().getRendererVersion()).isEqualTo("semantic-svg-v1");
    }
}
