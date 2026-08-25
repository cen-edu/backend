package com.cenedu.backend.domain.problem.authoring.edit.semantic;

/**
 * 수정 한 건이 어떤 방식으로 처리됐는지 나타낸다.
 *
 * <p>{@code CHOICE_REORDER}는 보기 내용과 정답을 그대로 두고 노출 순서만 바꾼다. 표현만 바꾸는
 * {@code PRESENTATIONAL_PATCH}로 묶을 수 없는 이유는, 순서가 바뀌면 정답이 가리키는 보기 키가
 * 함께 바뀌어 "정답 단위가 그대로여야 한다"는 표현 수정의 불변식을 깨기 때문이다.
 *
 * <p>{@code BANK_REUSE}는 실행 결과에만 나타난다 — 교체 요청을 AI 재생성 없이 문제은행의 기존
 * 문항으로 해결한 경우다. Agent가 만드는 {@code ProblemSemanticPatch.mode}로는 쓰이지 않으며,
 * {@code ProblemSemanticPatchClassifier}도 이 값을 반환하지 않는다. 이 값을
 * {@code STRUCTURAL_REGENERATION}으로 뭉뚱그리면 화면이 "AI가 새로 만든 문제"와 "은행에서
 * 찾아온 문제"를 구분하지 못한다.
 */
public enum SemanticEditMode {
    PRESENTATIONAL_PATCH, PARAMETRIC_PATCH, CHOICE_REORDER, STRUCTURAL_REGENERATION,
    BANK_REUSE, RESTORE, REJECTED
}
