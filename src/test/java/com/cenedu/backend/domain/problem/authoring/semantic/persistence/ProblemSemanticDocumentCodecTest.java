package com.cenedu.backend.domain.problem.authoring.semantic.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProblemSemanticDocumentCodecTest {
    @Test void semanticallyIdenticalModelsHaveOneCanonicalHash() {
        var model = new ProblemSemanticModelV1(1, new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L, "major", "middle", "sub"),
                new SemanticProblemIntent(QuestionType.SHORT_INPUT, "mid", null, "compute", "x", 1, false), List.of(), List.of(), List.of(),
                new SemanticPresentationPlan("question", List.of(), List.of(), "explanation", null, List.of()), List.of(), List.of());
        var codec = new ProblemSemanticDocumentCodec(new ObjectMapper());
        var first = codec.semanticModel(model); var second = codec.semanticModel(model);
        assertThat(second.json()).isEqualTo(first.json());
        assertThat(second.sha256()).matches("[0-9a-f]{64}").isEqualTo(first.sha256());
        assertThat(codec.readSemanticModel(first.json()).schemaVersion()).isEqualTo(1);
    }

    @Test void renderSpecRoundTripsPolymorphicDiagramSpec() {
        var spec = new com.cenedu.backend.domain.problem.authoring.diagram.NumberLineDiagramSpecV1(1, "A",
                com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind.NUMBER_LINE,
                new com.cenedu.backend.domain.problem.authoring.diagram.DiagramViewport(320, 120, 8),
                new com.cenedu.backend.domain.problem.authoring.diagram.DiagramStyle("#000", "#FFF", "#F00", 1, "sans", 12),
                "MIN", "MAX", "STEP", List.of(), List.of(), true, true);
        var codec = new ProblemSemanticDocumentCodec(new ObjectMapper());
        var document = codec.renderSpec(spec, "semantic-svg-v1");
        assertThat(codec.readRenderSpec(document.json())).isInstanceOf(com.cenedu.backend.domain.problem.authoring.diagram.NumberLineDiagramSpecV1.class);
    }
}
