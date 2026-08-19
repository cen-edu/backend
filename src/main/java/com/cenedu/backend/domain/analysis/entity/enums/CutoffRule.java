package com.cenedu.backend.domain.analysis.entity.enums;

/**
 * 상태 판정에 실제로 적용된 컷오프 기준.
 *
 * <p>문항 수에 따라 자동으로 갈린다. 판정 이력에 남겨 두면 컷오프 정책이 바뀐 뒤에도 과거
 * 판정이 어느 기준으로 나온 값인지 재현할 수 있다.
 */
public enum CutoffRule {

    /** N >= 4. 정답률 80% / 40% 컷오프. */
    RATIO,

    /** N <= 3. 통계적 오차를 피해 오답 개수로 판정한다. */
    WRONG_COUNT
}
