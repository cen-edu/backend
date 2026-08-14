package com.cenedu.backend.domain.analysis.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;

/** 종합평가의 문항 열과 학생별 점수·채점 상태·풀이시간 행렬. */
public record ComprehensiveAssessmentItemAchievementResponse(
        List<AssessmentItemColumn> items,
        List<AssessmentStudentAchievement> students
) {
    public ComprehensiveAssessmentItemAchievementResponse {
        items = List.copyOf(items);
        students = List.copyOf(students);
    }

    public record AssessmentItemColumn(
            Long worksheetItemId,
            int itemNumber,
            BigDecimal maxScore
    ) {
    }

    public record AssessmentStudentAchievement(
            Long studentId,
            String studentName,
            List<AssessmentItemResult> results
    ) {
        public AssessmentStudentAchievement {
            results = List.copyOf(results);
        }
    }

    public record AssessmentItemResult(
            Long worksheetItemId,
            GradingStatus gradingStatus,
            BigDecimal score,
            Long solvingDurationMs
    ) {
    }
}
