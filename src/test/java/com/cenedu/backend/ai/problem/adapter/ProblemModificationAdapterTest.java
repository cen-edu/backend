package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.cenedu.backend.ai.problem.ProblemStructuredOutputSchemas;
import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;

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
}
