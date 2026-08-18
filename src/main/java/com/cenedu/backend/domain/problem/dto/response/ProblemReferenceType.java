package com.cenedu.backend.domain.problem.dto.response;

/** 학습지 후보가 기존 문제은행인지 AI 작성 Session인지 구분한다. */
public enum ProblemReferenceType {
    EXISTING_QUESTION,
    AUTHORING_SESSION
}
