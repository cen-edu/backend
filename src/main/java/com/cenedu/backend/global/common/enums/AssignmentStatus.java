package com.cenedu.backend.global.common.enums;

/** 학생 한 명의 학습지 진행 상태. worksheet 가 쓰고 submission·grading 이 읽는다. */
public enum AssignmentStatus {
    NOT_STARTED,
    SUBMITTED,
    GRADED,
    NOT_SUBMITTED
}
