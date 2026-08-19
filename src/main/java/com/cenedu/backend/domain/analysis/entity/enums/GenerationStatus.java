package com.cenedu.backend.domain.analysis.entity.enums;

/** AI 분석 문장의 생성 상태. */
public enum GenerationStatus {

    /** AI 문장 생성 전. */
    PENDING,

    /** AI 문장 생성 중. */
    GENERATING,

    /** 전체 AI 문장 생성 및 저장 완료. */
    READY,

    /** LLM 호출 실패, 응답 형식 오류 또는 검증 실패. */
    FAILED
}
