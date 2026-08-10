package com.cenedu.backend.domain.analysis.reissue;

import java.util.List;

/**
 * 재출제 후보로 쓸 수 있는 뱅크 문항 하나.
 *
 * <p>뱅크 원본에서 선정에 필요한 값만 뽑아 담는다. 문항 본문 전체를 메모리에 들고 있을 이유가
 * 없다.
 *
 * @param evaluationArea 원본 라벨에서 온 평가 영역. 없으면 {@code null}이고 채우지 않는다.
 * @param difficulty     원본 난이도. 숫자가 아니라 이넘으로만 다룬다.
 * @param stages         빈칸별 풀이 구간. LLM이 붙인 미검증 라벨이라 선정에는 쓰지 않고 빈칸
 *                       수를 세거나 교사 화면에 위치를 표시할 때만 쓴다.
 */
public record BankQuestion(
        String id,
        String unitName,
        String evaluationArea,
        QuestionDifficulty difficulty,
        boolean imageFree,
        List<String> stages,
        String promptText
) {
    public BankQuestion {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    /** 겨냥 구간이 몇 번째 빈칸인지. 없으면 {@code -1}. 교사 화면 표시 전용이다. */
    public int stagePosition(String stage) {
        return stage == null ? -1 : stages.indexOf(stage);
    }
}
