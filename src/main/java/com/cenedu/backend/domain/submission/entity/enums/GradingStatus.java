package com.cenedu.backend.domain.submission.entity.enums;

/** 답안 한 칸의 채점 상태. submission_answer.grading_status 의 DB 값과 이름이 일치한다. */
public enum GradingStatus {
    NOT_GRADED,
    GRADED,
    FAILED
}
