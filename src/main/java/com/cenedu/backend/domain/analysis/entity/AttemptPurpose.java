package com.cenedu.backend.domain.analysis.entity;

/**
 * 응답을 무엇을 보려고 냈는지.
 *
 * <p>진단 응답만 상태를 움직인다. 응용 응답은 이미 습득한 개념을 다른 맥락에 옮길 수 있는지
 * 확인하려고 낸 것이라, 틀려도 그 단계가 취약하다는 근거가 되지 않는다. 새 맥락을 읽지 못한
 * 것인지 개념을 잊은 것인지 구분할 방법이 없다.
 */
public enum AttemptPurpose {

    /** 같은 단계를 다시 관찰한다. 동일 문제·유사 문제가 여기 해당한다. */
    DIAGNOSTIC,

    /** 다른 맥락으로 옮길 수 있는지 본다. 상태 계산에 넣지 않는다. */
    APPLIED
}
