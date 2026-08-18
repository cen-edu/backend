package com.cenedu.backend.ai.verification.adapter;

import java.util.List;
import java.util.Map;

/**
 * Solver 가 혼자 푼 결과. 파싱된 형태다.
 *
 * @param solved  못 풀었다고 응답했으면 {@code false}. 이 경우 판정은 UNVERIFIABLE 이다
 * @param answers unitKey → 답 문자열. 객관식은 choiceKey 를 담는다
 * @param reason  한 줄 근거. <b>전체 풀이 과정은 담지 않는다</b> — 로깅 정책과 같은 이유다
 */
public record SolverAnswer(boolean solved, Map<String, String> answers, String reason) {

    public SolverAnswer {
        answers = answers == null ? Map.of() : Map.copyOf(answers);
    }

    static SolverAnswer unsolved(String reason) {
        return new SolverAnswer(false, Map.of(), reason);
    }

    /**
     * Solver 를 부르지 않았을 때. 서술형이 그렇다.
     *
     * <p>{@code unsolved} 와 값은 같지만 의미가 다르다. 부르지 않은 것과 불러서 못 푼 것을 같은
     * 이름으로 두면, 호출을 빠뜨린 버그가 "모델이 못 풀었다"로 보인다.
     */
    static SolverAnswer notCalled() {
        return new SolverAnswer(false, Map.of(), null);
    }

    /** {@code MAIN} 한 칸만 있는 응답. 객관식·단답형·서술형이 여기 해당한다. */
    String answerFor(String unitKey) {
        return answers.get(unitKey);
    }

    List<String> missingKeys(List<String> requiredUnitKeys) {
        return requiredUnitKeys.stream().filter(key -> !answers.containsKey(key)).toList();
    }
}
