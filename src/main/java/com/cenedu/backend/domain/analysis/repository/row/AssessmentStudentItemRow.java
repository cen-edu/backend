package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;

import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;

/** 학생 한 명의 종합평가 문항별 점수·채점 상태·풀이시간 행. */
public record AssessmentStudentItemRow(
        Long studentId,
        String studentName,
        Long worksheetItemId,
        GradingStatus gradingStatus,
        BigDecimal score,
        Long solvingDurationMs
) {
}
