package com.cenedu.backend.domain.analysis.reissue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 관찰된 지점으로 다음에 낼 문항을 고른다. 순수 함수라 DB도 HTTP도 모른다.
 *
 * <p>하드 필터는 없으면 출제가 멈추는 조건이고, 정렬은 있으면 더 좋은 조건이다. 둘을 섞지
 * 않는다.
 *
 * <p>평가 영역을 하드 필터에 넣지 않는 이유는 재고다. 소단원·난이도·영역을 모두 걸면 빈 칸이
 * 생긴다. 소인수분해 상 난이도의 문제해결 문항은 0개다.
 */
public final class ReissueSelector {

    /** 한 번에 내보내는 문항 수. 판정 규칙(서로 다른 문항 2개, 연속 3회)과 맞춘 값이다. */
    public static final int SET_SIZE = 3;

    private ReissueSelector() {
    }

    /**
     * @param seed 같은 학생이 다시 요청해도 같은 세트를 받도록 고정하는 값. 보통 학생 ID다.
     *             새로고침할 때마다 문항이 바뀌면 교사가 무엇을 냈는지 확인할 수 없다.
     */
    public static Result select(
            List<BankQuestion> bank,
            ReissueTarget target,
            Set<String> alreadyServed,
            String seed,
            int limit
    ) {
        if (bank == null || target == null) {
            throw new IllegalArgumentException("뱅크와 겨냥 지점이 필요합니다.");
        }
        List<BankQuestion> pool = hardFilter(bank, target, alreadyServed);
        Map<String, Double> jitter = jitter(pool, seed);
        List<BankQuestion> picked = pool.stream()
                .sorted(ranking(jitter))
                .limit(Math.max(0, limit))
                .toList();
        return new Result(picked, pool.size());
    }

    public static Result select(
            List<BankQuestion> bank, ReissueTarget target,
            Set<String> alreadyServed, String seed) {
        return select(bank, target, alreadyServed, seed, SET_SIZE);
    }

    /**
     * 없으면 출제가 멈추는 조건. 하나라도 어긋나면 후보에서 뺀다.
     *
     * <p>구간 순서가 자기모순인 문항을 걸러내던 조건은 뺐다. 그 검사는 풀이 구간 분류를 보는
     * 것인데, 분류는 동일·유사 문항 선정에 쓰지 않기로 했다. 라벨의 정합성은 그 라벨을 실제로
     * 쓰는 곳(응용 문항 생성, 교사용 문장)에서 보면 된다.
     */
    private static List<BankQuestion> hardFilter(
            List<BankQuestion> bank, ReissueTarget target, Set<String> alreadyServed) {
        Set<String> served = alreadyServed == null ? Set.of() : alreadyServed;
        List<BankQuestion> pool = new ArrayList<>();
        for (BankQuestion question : bank) {
            boolean keep = target.unitName().equals(question.unitName())
                    && target.difficulty() == question.difficulty()
                    && question.imageFree()
                    && !served.contains(question.id());
            if (keep) {
                pool.add(question);
            }
        }
        return pool;
    }

    /**
     * 있으면 더 좋은 조건. 순서가 곧 우선순위다.
     *
     * <ol>
     *   <li>빈칸 총수가 적음 — 구간이 적을수록 학생이 풀 것도 해석할 것도 적다</li>
     *   <li>무작위 — 조건이 같은 학생들에게 같은 세트가 나가는 것을 막는다</li>
     * </ol>
     *
     * <p><b>문항 분류는 하나도 쓰지 않는다.</b> 평가 영역(이해·계산·추론·문제해결), 소주제,
     * 풀이 구간 모두 선정에서 뺐다. 남은 기준은 문항의 구조(빈칸 수)뿐이다.
     *
     * <p>이 선택의 대가는 측정되어 있다. 소단원·난이도만으로 고르면 원본과 유사도가 0.7 이상일
     * 확률이 3% 수준이다. 평가 영역을 더하면 8%, 소주제까지 더하면 65%였다. 분류를 쓰지 않기로
     * 한 이상 유사도는 이 자리에서 올릴 수 없고, 올리려면 문항 본문 임베딩처럼 분류가 아닌
     * 신호를 따로 들여야 한다.
     */
    private static Comparator<BankQuestion> ranking(Map<String, Double> jitter) {
        return Comparator
                .comparingInt((BankQuestion question) -> question.stages().size())
                .thenComparingDouble(question -> jitter.get(question.id()));
    }

    private static Map<String, Double> jitter(List<BankQuestion> pool, String seed) {
        Random random = new Random(seed == null ? 0 : seed.hashCode());
        Map<String, Double> values = new HashMap<>();
        for (BankQuestion question : pool) {
            values.put(question.id(), random.nextDouble());
        }
        return values;
    }

    /**
     * 무엇을 겨냥해 고르는지.
     *
     * <p>소단원과 난이도뿐이다. 평가 영역·소주제·풀이 구간은 선정에 쓰지 않기로 했다. 관찰된
     * 평가 영역과 겨냥 구간은 교사 화면에 표시하지만 여기로는 넘기지 않는다.
     */
    public record ReissueTarget(
            String unitName,
            QuestionDifficulty difficulty
    ) {
        public ReissueTarget {
            if (unitName == null || unitName.isBlank() || difficulty == null) {
                throw new IllegalArgumentException("소단원과 난이도는 필수입니다.");
            }
        }
    }

    /**
     * @param candidateCount 하드 필터를 통과한 문항 수. 재고가 마르는지 보려면 이 값을 본다.
     */
    public record Result(List<BankQuestion> questions, int candidateCount) {
        public Result {
            questions = questions == null ? List.of() : List.copyOf(questions);
        }
    }
}
