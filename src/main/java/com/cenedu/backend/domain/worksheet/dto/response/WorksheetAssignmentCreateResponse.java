package com.cenedu.backend.domain.worksheet.dto.response;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;

/** 학습지 배포 결과. */
public record WorksheetAssignmentCreateResponse(
        Long assignmentId,
        String className,
        int studentCount,
        OffsetDateTime dueAt
) {

    /** 배포 엔티티와 반 이름·학생 수를 배포 결과 응답으로 변환한다. */
    public static WorksheetAssignmentCreateResponse from(
            WorksheetAssignment assignment, String className, int studentCount
    ) {
        return new WorksheetAssignmentCreateResponse(
                assignment.getId(), className, studentCount, assignment.getDueAt());
    }
}
