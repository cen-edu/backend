package com.cenedu.backend.domain.worksheet.dto.response;

import java.util.List;

/**
 * 복습 화면의 해설 묶음. 프론트가 필드 유무로 섹션을 켜고 끄므로, 없는 것은 {@code null}로 두면
 * 화면이 알아서 접힌다.
 *
 * <p>{@code steps}는 빈칸형 문항에만 값이 있다 — {@code problem_step} 행을 가지는 형식이
 * 빈칸형뿐이다(실측). 학습지 유형이 아니라 문항 형식이 이 축을 가른다 — 명세 8.4의 유형 축과
 * 의도적으로 다르며 근거는 {@code StudentResultQueryService#buildExplanation} 에 있다.
 */
public record StudentResultExplanationResponse(
        String answerText,
        String summary,
        List<StudentResultStepResponse> steps,
        StudentResultConceptResponse concept
) {
}
