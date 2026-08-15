package com.cenedu.backend.domain.worksheet.dto.response;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.repository.row.WorksheetListRow;

/** 문제 보관함 목록의 학습지 한 줄. */
public record WorksheetListItemResponse(
        Long worksheetId,
        String title,
        String type,
        String origin,
        short grade,
        String semester,
        String unitSummary,
        OffsetDateTime createdAt,
        long problemCount,
        Short totalScore,
        Long sourceWorksheetId,
        long assignmentCount
) {

    /** 목록 행과 집계 값들을 목록 응답 한 줄로 변환한다. */
    public static WorksheetListItemResponse from(
            WorksheetListRow row, String unitSummary, long problemCount, long assignmentCount
    ) {
        return new WorksheetListItemResponse(
                row.id(),
                row.title(),
                WorksheetResponseFormatter.toApiType(row.type()),
                WorksheetResponseFormatter.toApiOrigin(row.origin()),
                row.grade(),
                WorksheetResponseFormatter.toApiSemester(row.semester()),
                unitSummary,
                row.createdAt(),
                problemCount,
                row.totalScore(),
                row.sourceWorksheetId(),
                assignmentCount
        );
    }
}
