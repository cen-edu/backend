package com.cenedu.backend.global.common.enums;

/** 채점 비교 방법. problem 이 정답에, submission 이 실제 적용값 스냅샷에 쓴다. */
public enum CompareMethod {
    CHOICE,
    VALUE,
    EXACT,
    SET,
    SUBST,
    RUBRIC
}
