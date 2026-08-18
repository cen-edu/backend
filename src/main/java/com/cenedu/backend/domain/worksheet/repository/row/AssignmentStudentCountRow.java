package com.cenedu.backend.domain.worksheet.repository.row;

/** 배포별 학생 수와 제출 수. 배포마다 조회하지 않으려고 한 번에 묶어 센다. */
public record AssignmentStudentCountRow(Long assignmentId, long studentCount, long submittedCount) {
}
