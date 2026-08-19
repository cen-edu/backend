package com.cenedu.backend.domain.worksheet.repository.row;

import java.time.OffsetDateTime;

/**
 * 원본 배정 하나의 맞춤 학습지 축 집계.
 *
 * <p>{@code assessmentWorksheetCount}가 0이면 채점 집계를 낼 대상이 없다 — 채점 상태는 종합평가
 * 학습지에만 있기 때문이다({@code LearningStatusStudentResponse}).
 */
public record CustomLearningWorksheetCountRow(
        Long sourceAssignmentId,
        long worksheetCount,
        long assessmentWorksheetCount,
        OffsetDateTime latestAssignedAt,
        OffsetDateTime latestDueAt
) {
}
