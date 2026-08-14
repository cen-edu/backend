package com.cenedu.backend.domain.worksheet.dto.response;

import java.util.List;

/** 학생 홈 배정 목록 응답. */
public record StudentAssignmentListResponse(List<StudentAssignmentResponse> assignments) {

    public static StudentAssignmentListResponse from(List<StudentAssignmentResponse> assignments) {
        return new StudentAssignmentListResponse(List.copyOf(assignments));
    }
}
