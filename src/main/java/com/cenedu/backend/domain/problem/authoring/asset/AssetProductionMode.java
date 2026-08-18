package com.cenedu.backend.domain.problem.authoring.asset;

/** 문제 자산을 서버 렌더링, 이미지 모델, 기존 자산 중 어떤 방식으로 준비할지 구분한다. */
public enum AssetProductionMode {
    STRUCTURED_RENDER,
    GENERATIVE_IMAGE,
    REUSE_EXISTING
}
