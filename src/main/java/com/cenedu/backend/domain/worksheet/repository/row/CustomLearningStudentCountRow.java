package com.cenedu.backend.domain.worksheet.repository.row;

/**
 * 원본 배정 하나의 맞춤 학습 학생 축 집계.
 *
 * <p>{@code studentCount}는 실인원({@code distinct studentId})이고 상태 네 값은 연인원이다 —
 * 한 학생이 맞춤을 세 번 받으면 실인원 1, 상태 합 3이다. 상태 네 값의 합은 배정 건수와 같다.
 */
public record CustomLearningStudentCountRow(
        Long sourceAssignmentId,
        long studentCount,
        long notStarted,
        long inProgress,
        long submitted,
        long notSubmitted,
        long gradingPending,
        long gradingDone
) {
}
