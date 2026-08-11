package com.cenedu.backend.domain.problem.entity.enums;

/** 문항의 출처. problem_question.source_type 의 DB 값과 이름이 일치한다. */
public enum QuestionSourceType {
    IMPORTED,
    GENERATED,
    RUNTIME
}
