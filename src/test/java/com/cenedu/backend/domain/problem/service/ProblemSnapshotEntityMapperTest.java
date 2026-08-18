package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

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
}
