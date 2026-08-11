package com.cenedu.backend.domain.problem.entity.enums;

/** 취약점 분석용 채점 단위 4분류. problem_answer_unit.diagnostic_type 의 DB 값과 이름이 일치한다. */
public enum DiagnosticType {
    INTERPRET,
    MODEL,
    EXECUTE,
    ANSWER
}
