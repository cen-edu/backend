package com.cenedu.backend.domain.problem.authoring.candidate;

/** 문제 후보가 문제은행 재사용·AI 생성·AI 수정 중 어떤 경로에서 나왔는지 구분한다. */
public enum CandidateSourceType {
    BANK_REUSE,
    AI_GENERATE,
    AI_MODIFY
}
