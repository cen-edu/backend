package com.cenedu.backend.domain.problem.authoring.edit.semantic;

/**
 * semantic model에서 한 자리를 바꾸는 연산의 종류다.
 *
 * <p>{@code SET_CHOICE_ORDER}는 보기의 내용이 아니라 노출 순서만 바꾼다. 순서가 바뀌면 정답이
 * 가리키는 보기 키도 함께 바뀌므로 다른 표현 수정과 같은 mode로 묶을 수 없다.
 */
public enum SemanticPatchOperationType {
    SET_PARAMETER_VALUE, SET_PARAMETER_UNIT, SET_TEMPLATE_TEXT, SET_DIAGRAM_STYLE, SET_LABEL_TEXT,
    SET_CHOICE_ORDER
}
