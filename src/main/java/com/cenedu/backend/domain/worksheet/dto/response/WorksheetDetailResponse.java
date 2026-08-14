package com.cenedu.backend.domain.worksheet.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.worksheet.entity.Worksheet;

/** 학습지 상세 응답. */
public record WorksheetDetailResponse(
        Long worksheetId,
        String title,
        String type,
        String origin,
        String status,
        short grade,
        String semester,
        Short totalScore,
        OffsetDateTime createdAt,
        List<WorksheetAssignmentHistoryResponse> assignments,
        List<WorksheetDetailItemResponse> items
) {

    /** 학습지 엔티티와 배포 이력·문항 목록을 학습지 상세 응답으로 변환한다. */
    public static WorksheetDetailResponse from(
            Worksheet worksheet,
            List<WorksheetAssignmentHistoryResponse> assignments,
            List<WorksheetDetailItemResponse> items
    ) {
        return new WorksheetDetailResponse(
                worksheet.getId(),
                worksheet.getTitle(),
                WorksheetResponseFormatter.toApiType(worksheet.getType()),
                WorksheetResponseFormatter.toApiOrigin(worksheet.getOrigin()),
                assignments.isEmpty() ? "draft" : "assigned",
                worksheet.getGrade(),
                WorksheetResponseFormatter.toApiSemester(worksheet.getSemester()),
                worksheet.getTotalScore(),
                worksheet.getCreatedAt(),
                List.copyOf(assignments),
                List.copyOf(items)
        );
    }
}
