package com.cenedu.backend.domain.analysis.entity;

/** 보고서 상태. AI 초안에서 시작해 교사가 고치고 확정한다. */
public enum ReportStatus {

    /** LLM 이 생성한 초안. 교사가 아직 손대지 않았다. */
    AI_DRAFT,

    /** 교사가 수정했다. 확정 전이라 되돌릴 수 있다. */
    TEACHER_EDITED,

    /** 교사가 확정했다. 학생에게 보이는 것은 이 상태부터다. */
    TEACHER_CONFIRMED
}
