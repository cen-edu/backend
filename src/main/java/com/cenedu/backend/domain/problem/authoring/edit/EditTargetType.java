package com.cenedu.backend.domain.problem.authoring.edit;

/** 교사가 선택하거나 Agent가 해석할 수정 영역을 S1 구조와 매핑한다. */
public enum EditTargetType {
    QUESTION_BODY,
    CONTENT_BLOCK,
    CHOICE,
    STEP,
    ANSWER_UNIT,
    EXPLANATION,
    LEARNING_GUIDE,
    RUBRIC_ITEM,
    ASSET,
    QUESTION_TYPE,
    DIFFICULTY,
    WHOLE_QUESTION
}
