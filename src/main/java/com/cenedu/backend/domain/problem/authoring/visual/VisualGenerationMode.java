package com.cenedu.backend.domain.problem.authoring.visual;

/** 시각 자산 생성 경로가 이미지 생성 여부와 원본 보존 여부를 결정한다. */
public enum VisualGenerationMode {
    NONE,
    AUTO,
    /** 로컬 검증 등에서 시각 자료 생성을 반드시 요구한다. */
    REQUIRED,
    PRESERVE_ORIGIN
}
