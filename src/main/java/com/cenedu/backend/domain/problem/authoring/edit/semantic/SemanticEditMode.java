package com.cenedu.backend.domain.problem.authoring.edit.semantic;

/**
 * 수정 한 건이 어떤 방식으로 처리됐는지 나타낸다.
 *
 * <p>{@code BANK_REUSE}는 실행 결과에만 나타난다 — 교체 요청을 AI 재생성 없이 문제은행의 기존
 * 문항으로 해결한 경우다. Agent가 만드는 {@code ProblemSemanticPatch.mode}로는 쓰이지 않으며,
 * {@code ProblemSemanticPatchClassifier}도 이 값을 반환하지 않는다. 이 값을
 * {@code STRUCTURAL_REGENERATION}으로 뭉뚱그리면 화면이 "AI가 새로 만든 문제"와 "은행에서
 * 찾아온 문제"를 구분하지 못한다.
 */
public enum SemanticEditMode {
    PRESENTATIONAL_PATCH, PARAMETRIC_PATCH, STRUCTURAL_REGENERATION, BANK_REUSE, RESTORE, REJECTED
}
