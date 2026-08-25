package com.cenedu.backend.domain.problem.authoring.semantic.materialization;

import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.authoring.semantic.validation.SemanticValidationException;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticAnswerExposureMaterializerTest {
    @Test
    void equationLabelThatMatchesCorrectChoiceIsRejectedBeforeAssetVerification() {
        assertThatThrownBy(() -> new DefaultProblemSemanticMaterializer().materialize(model("$y={{K}}x$")))
                .isInstanceOf(SemanticValidationException.class)
                .hasMessageContaining("정답 보기 내용을 직접 노출");
    }

    @Test
    void unlabeledGraphKeepsObservableShapeAndDoesNotInventEquationInAltText() {
        var materialized = new DefaultProblemSemanticMaterializer().materialize(model(""));

        assertThat(materialized.snapshot().assets()).singleElement().satisfies(asset -> {
            assertThat(asset.altText()).contains("원점을 지나는 직선");
            assertThat(asset.altText()).doesNotContain("y=2x");
        });
    }

    @Test
    void pointLabelThatMatchesCorrectChoiceIsRejectedToo() {
        var pointLabeled = model("");
        var graph = (CoordinateGraphDiagramSpecV1) pointLabeled.diagrams().getFirst();
        var point = new CoordinatePointSpec("P1", "X", "Y", "y=2x", PointMarker.CLOSED_CIRCLE);
        var replaced = new CoordinateGraphDiagramSpecV1(1, "F1", DiagramKind.COORDINATE_GRAPH,
                graph.viewport(), graph.style(), graph.xMinKey(), graph.xMaxKey(), graph.yMinKey(), graph.yMaxKey(),
                graph.xTickKey(), graph.yTickKey(), List.of(point), graph.segments(), graph.lines(), graph.functions());
        var modelWithPoint = new ProblemSemanticModelV1(1, pointLabeled.curriculum(), pointLabeled.intent(),
                pointLabeled.parameters(), pointLabeled.computations(), pointLabeled.constraints(),
                pointLabeled.presentation(), List.of(replaced), pointLabeled.assertions());

        assertThatThrownBy(() -> new DefaultProblemSemanticMaterializer().materialize(modelWithPoint))
                .isInstanceOf(SemanticValidationException.class)
                .hasMessageContaining("points[P1].labelTemplate");
    }

    private ProblemSemanticModelV1 model(String labelTemplate) {
        var intent = new SemanticProblemIntent(QuestionType.MULTIPLE_CHOICE, "mid", null,
                "read graph", "EQ1", 1, true);
        var presentation = new SemanticPresentationPlan(
                "그래프의 식으로 알맞은 것을 고르시오.",
                List.of(new SemanticChoiceTemplate("C1", 0, "$y=2x$", "EQ1"),
                        new SemanticChoiceTemplate("C2", 1, "$y=x+1$", "EQ2")),
                List.of(), "그래프를 읽어 관계를 확인한다.", null, List.of());
        var graph = new CoordinateGraphDiagramSpecV1(1, "F1", DiagramKind.COORDINATE_GRAPH,
                new DiagramViewport(400, 240, 16),
                new DiagramStyle("#000000", "#FFFFFF", "#FF0000", 1, "sans-serif", 12),
                "X_MIN", "X_MAX", "Y_MIN", "Y_MAX", null, null, List.of(), List.of(), List.of(),
                List.of(new CoordinateFunctionSpec("FN1", CoordinateFunctionKind.DIRECT_PROPORTION, "K", labelTemplate)));
        return new ProblemSemanticModelV1(1,
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L, "대", "중", "소"),
                intent,
                List.of(new SemanticParameter("K", SemanticValueType.INTEGER, "2", null, false, null),
                        new SemanticParameter("EQ1", SemanticValueType.TEXT, "y=2x", null, false, null),
                        new SemanticParameter("EQ2", SemanticValueType.TEXT, "y=x+1", null, false, null),
                        new SemanticParameter("X_MIN", SemanticValueType.INTEGER, "-10", null, false, null),
                        new SemanticParameter("X_MAX", SemanticValueType.INTEGER, "10", null, false, null),
                        new SemanticParameter("Y_MIN", SemanticValueType.INTEGER, "-10", null, false, null),
                        new SemanticParameter("Y_MAX", SemanticValueType.INTEGER, "10", null, false, null)),
                List.of(), List.of(), presentation, List.of(graph), List.of());
    }
}
