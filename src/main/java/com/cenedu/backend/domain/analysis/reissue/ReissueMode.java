package com.cenedu.backend.domain.analysis.reissue;

/** 다음 문항을 무엇 때문에 내는지. 목적이 다르면 고르는 방법도 다르다. */
public enum ReissueMode {

    /**
     * 같은 문항을 다시. 시스템 오류로 학생이 답을 쓰지 못했을 때만 쓴다. 답을 쓴 학생에게 같은
     * 문항을 다시 내면 기억을 확인할 뿐 실력을 확인하지 못한다.
     */
    SAME,

    /** 뱅크에서 유사 문항. 오류가 반복되는지, 어느 지점에서 막히는지 본다. */
    SIMILAR,

    /** 응용 문항. 이미 확인 기준을 채운 학생이 다른 맥락으로 옮기는지 본다. */
    APPLIED
}
