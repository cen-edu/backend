package com.cenedu.backend.infra.vector;

import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DeterministicMmrSelector {
    /** 중복 cluster·source family를 제거하며 결정적인 MMR 순서로 참고 문제를 선택한다. */
    public List<ProblemSearchCandidate> select(List<ProblemSearchCandidate> candidates, List<Float> queryVector,
                                                int selectionLimit, double lambda, QuestionType questionType,
                                                String difficulty) {
        List<ProblemSearchCandidate> remaining = new ArrayList<>(candidates == null ? List.of() : candidates);
        List<ProblemSearchCandidate> selected = new ArrayList<>();
        Set<String> clusters = new HashSet<>(), families = new HashSet<>();
        while (selected.size() < selectionLimit && !remaining.isEmpty()) {
            final List<ProblemSearchCandidate> chosen = selected;
            ProblemSearchCandidate best = remaining.stream()
                    .filter(candidate -> !clusters.contains(candidate.duplicateClusterKey())
                            && !families.contains(candidate.sourceFamilyKey()))
                    .max(Comparator.comparingDouble((ProblemSearchCandidate c) -> score(c, queryVector, chosen, lambda, questionType, difficulty))
                            .thenComparing(ProblemSearchCandidate::denseRank, Comparator.reverseOrder())
                            .thenComparing(ProblemSearchCandidate::questionId, Comparator.reverseOrder()))
                    .orElse(null);
            if (best == null) break;
            selected.add(best); remaining.remove(best);
            clusters.add(best.duplicateClusterKey()); families.add(best.sourceFamilyKey());
        }
        return List.copyOf(selected);
    }

    private double score(ProblemSearchCandidate candidate, List<Float> query, List<ProblemSearchCandidate> selected,
                         double lambda, QuestionType type, String difficulty) {
        double relevance = Math.min(1.0, candidate.denseScore()
                + (difficulty != null && difficulty.equals(candidate.difficulty()) ? 0.02 : 0)
                + (type == candidate.questionType() ? 0.01 : 0));
        double diversity = selected.stream().mapToDouble(other -> VectorCodec.cosineSimilarity(candidate.vector(), other.vector())).max().orElse(0);
        return lambda * relevance - (1 - lambda) * diversity;
    }
}
