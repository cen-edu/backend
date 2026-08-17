package com.cenedu.backend.domain.problem.authoring.edit;

/** 문제 교체 시 문제은행 탐색과 AI 생성 중 어느 경로를 쓸지 결정한다. */
public enum ReplacementSourcePolicy {
    NONE,
    BANK_FIRST,
    GENERATE_ONLY
}
