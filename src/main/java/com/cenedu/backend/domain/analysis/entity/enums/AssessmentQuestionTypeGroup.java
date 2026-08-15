package com.cenedu.backend.domain.analysis.entity.enums;

import com.cenedu.backend.global.common.enums.QuestionType;

/** 종합평가 화면에서 사용하는 객관식·주관식·서술형 문항 분류. */
public enum AssessmentQuestionTypeGroup {
    MULTIPLE_CHOICE,
    SHORT_ANSWER,
    ESSAY;

    /** 문제 은행의 세부 문항 유형을 화면의 세 분류로 묶는다. */
    public static AssessmentQuestionTypeGroup from(QuestionType questionType) {
        return switch (questionType) {
            case MULTIPLE_CHOICE -> MULTIPLE_CHOICE;
            case SHORT_INPUT, STEP_FILL -> SHORT_ANSWER;
            case ESSAY -> ESSAY;
        };
    }
}
