package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;

/** 종합평가 문항 성취 표의 문항 열 정보. */
public record AssessmentItemColumnRow(
        Long worksheetItemId,
        int itemNumber,
        BigDecimal maxScore
) {
}
