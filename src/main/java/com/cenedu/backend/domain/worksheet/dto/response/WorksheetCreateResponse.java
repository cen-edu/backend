package com.cenedu.backend.domain.worksheet.dto.response;

import com.cenedu.backend.domain.worksheet.entity.Worksheet;

/** 저장된 학습지의 생성 결과. */
public record WorksheetCreateResponse(
        Long worksheetId,
        String title,
        int problemCount,
        Short totalScore
) {

    /** 저장된 학습지와 문항 수를 생성 결과 응답으로 변환한다. */
    public static WorksheetCreateResponse from(Worksheet worksheet, int problemCount) {
        return new WorksheetCreateResponse(
                worksheet.getId(),
                worksheet.getTitle(),
                problemCount,
                worksheet.getTotalScore()
        );
    }
}
