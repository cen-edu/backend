package com.cenedu.backend.domain.grading.dto.response;

import java.util.List;

/** 평가 결과 목록(명세 4절). */
public record GradingWorksheetListResponse(List<GradingWorksheetItemResponse> worksheets) {
}
