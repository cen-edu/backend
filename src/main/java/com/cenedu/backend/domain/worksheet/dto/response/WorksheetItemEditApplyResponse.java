package com.cenedu.backend.domain.worksheet.dto.response;

/** 수정 세션 결과를 학습지 문항에 반영한 결과다. */
public record WorksheetItemEditApplyResponse(
        Long worksheetItemId,
        Long previousQuestionId,
        Long questionId,
        boolean replaced
) {
}
