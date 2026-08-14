package com.cenedu.backend.domain.worksheet.dto.response;

import java.util.List;

/** 문제 보관함 목록 응답. */
public record WorksheetListResponse(List<WorksheetListItemResponse> worksheets) {

    /** 목록 행들을 목록 응답으로 감싼다. */
    public static WorksheetListResponse from(List<WorksheetListItemResponse> worksheets) {
        return new WorksheetListResponse(List.copyOf(worksheets));
    }
}
