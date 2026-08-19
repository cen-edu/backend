package com.cenedu.backend.infra.vector;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicMmrSelectorTest {
    @Test
    void tieBreaksByDenseRankThenQuestionIdAndGuardsFamilies() {
        var selector = new DeterministicMmrSelector();
        List<ProblemSearchCandidate> selected = selector.select(
                List.of(candidate(13, 2, .80, "c2", "f2"), candidate(12, 1, .80, "c1", "f1"),
                        candidate(11, 1, .80, "c1", "f1")), List.of(1f, 0f), 3, .70,
                QuestionType.SHORT_INPUT, "mid");
        assertThat(selected).extracting(ProblemSearchCandidate::questionId).containsExactly(11L, 13L);
    }

    private static ProblemSearchCandidate candidate(long id, int rank, double score, String cluster, String family) {
        return new ProblemSearchCandidate(id, rank, score, java.util.stream.IntStream.range(0, 1024)
                .mapToObj(i -> i == 0 ? 1f : 0f).toList(), cluster, family, QuestionType.SHORT_INPUT, "mid", null, "hash");
    }
}
