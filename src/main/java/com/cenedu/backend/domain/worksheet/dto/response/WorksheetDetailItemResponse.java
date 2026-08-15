package com.cenedu.backend.domain.worksheet.dto.response;

import java.math.BigDecimal;

import com.cenedu.backend.domain.problem.dto.response.ProblemQuestionDetailResponse;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;

/** 학습지 상세의 문항 한 줄. */
public record WorksheetDetailItemResponse(
        Long worksheetItemId,
        int displayOrder,
        BigDecimal maxScore,
        String supportMode,
        String customStage,
        ProblemQuestionDetailResponse question
) {

    /** 문항 배치 엔티티와 상세 본문을 학습지 상세 문항 한 줄로 변환한다. */
    public static WorksheetDetailItemResponse from(
            WorksheetItem item, ProblemQuestionDetailResponse question
    ) {
        return new WorksheetDetailItemResponse(
                item.getId(),
                item.getDisplayOrder(),
                item.getMaxScore(),
                WorksheetResponseFormatter.toApiSupportMode(item.getSupportMode()),
                WorksheetResponseFormatter.toApiCustomStage(item.getCustomStage()),
                question
        );
    }
}
