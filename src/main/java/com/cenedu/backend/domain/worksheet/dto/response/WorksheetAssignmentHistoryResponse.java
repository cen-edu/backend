package com.cenedu.backend.domain.worksheet.dto.response;

import java.time.OffsetDateTime;

/** 학습지 상세의 「출제 이력」 한 줄. */
public record WorksheetAssignmentHistoryResponse(
        Long assignmentId,
        Long classId,
        String className,
        String status,
        OffsetDateTime assignedAt,
        OffsetDateTime dueAt
) {

    /** 배포 정보와 대상 이름으로 dueAt 기준 파생 상태를 채운 출제 이력 한 줄을 만든다. */
    public static WorksheetAssignmentHistoryResponse of(
            Long assignmentId, Long classId, String className,
            OffsetDateTime assignedAt, OffsetDateTime dueAt, OffsetDateTime now
    ) {
        String status = dueAt.isAfter(now) ? "ongoing" : "completed";
        return new WorksheetAssignmentHistoryResponse(
                assignmentId, classId, className, status, assignedAt, dueAt);
    }
}
