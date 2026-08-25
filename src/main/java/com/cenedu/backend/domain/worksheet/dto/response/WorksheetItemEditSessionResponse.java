package com.cenedu.backend.domain.worksheet.dto.response;

/**
 * 학습지 문항 하나를 다시 수정하기 위해 연 작성 세션이다.
 *
 * <p>{@code sessionId}로 기존 문제 수정 대화 API
 * ({@code /api/teacher/problems/authoring-sessions/{sessionId}/edit/turns})를 그대로 사용하고,
 * 수정이 끝나면 같은 sessionId로 학습지 문항 교체를 확정한다.
 */
public record WorksheetItemEditSessionResponse(
        Long worksheetItemId,
        Long sessionId,
        Long currentVersionId,
        Long questionId
) {
}
