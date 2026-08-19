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
}
