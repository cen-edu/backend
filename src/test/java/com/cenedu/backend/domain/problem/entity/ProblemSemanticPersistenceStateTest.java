package com.cenedu.backend.domain.problem.entity;

import static org.assertj.core.api.Assertions.assertThat;
import com.cenedu.backend.domain.problem.authoring.semantic.persistence.SemanticModelDocument;
import com.cenedu.backend.domain.problem.entity.enums.*;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;

class ProblemSemanticPersistenceStateTest {
    @Test void questionStatusChangesAtomicallyWithSemanticTuple() {
        var question = ProblemQuestion.create(QuestionSourceType.IMPORTED, "dataset:1", "dataset", null, 1L, "topic", (short) 1,
                QuestionType.SHORT_INPUT, QuestionPresentation.TEXT_ONLY, "{}", "prompt", null, null, null, null);
        question.attachSemanticModel(new SemanticModelDocument(1, "{\"schemaVersion\":1}", "a".repeat(64)));
        assertThat(question.getSemanticModelStatus()).isEqualTo(SemanticModelStatus.READY);
        assertThat(question.getSemanticModelHash()).hasSize(64);
        question.markSemanticModelFailed();
        assertThat(question.getSemanticModelStatus()).isEqualTo(SemanticModelStatus.FAILED);
        assertThat(question.getSemanticModel()).isNull();
    }
}
