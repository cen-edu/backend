package com.cenedu.backend.domain.problem.authoring.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProblemReferenceQueryTest {
    @Test
    void personalizedQueryRequiresOneOriginSnapshot() {
        assertThatThrownBy(() -> new ProblemReferenceQuery(UUID.randomUUID(),
                GenerationPurpose.PERSONALIZED_APPLICATION, scope(), QuestionType.SHORT_INPUT,
                "mid", 10L, null, 40, 4, Set.of(10L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("맞춤 유사·응용 검색에는 ORIGIN ID와 Snapshot이 필요합니다.");
    }

    @Test
    void generalQueryRejectsOrigin() {
        assertThatThrownBy(() -> new ProblemReferenceQuery(UUID.randomUUID(),
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE, scope(), QuestionType.SHORT_INPUT,
                "mid", 10L, null, 40, 3, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("일반·종합평가 검색에는 ORIGIN을 지정할 수 없습니다.");
    }

    @Test
    void copiesExcludedIdsAndRejectsLimitsOutsideServerPolicy() {
        Set<Long> excluded = Set.of(1L, 2L);
        ProblemReferenceQuery query = new ProblemReferenceQuery(UUID.randomUUID(),
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE, scope(), QuestionType.SHORT_INPUT,
                "mid", null, null, 40, 3, excluded);
        assertThat(query.excludedQuestionIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThatThrownBy(() -> new ProblemReferenceQuery(UUID.randomUUID(),
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE, scope(), QuestionType.SHORT_INPUT,
                "mid", null, null, 41, 3, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CurriculumScope scope() {
        return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 30L,
                "대단원", "중단원", "소단원");
    }
}
