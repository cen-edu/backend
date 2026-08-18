package com.cenedu.backend.domain.analysis.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.entity.enums.AssessmentQuestionTypeGroup;
import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;
import com.cenedu.backend.domain.analysis.entity.enums.DiagnosticStage;
import com.cenedu.backend.domain.analysis.entity.enums.StudentItemResultType;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;
import com.cenedu.backend.global.common.enums.EvaluationArea;

/** 학생 상세 화면의 문항별 결과 목록. */
public record StudentItemResultListResponse(
        Long assignmentStudentId,
        List<StudentItemResult> items
) {
    public StudentItemResultListResponse {
        items = List.copyOf(items);
    }

    public record StudentItemResult(
            Long worksheetItemId,
            Long questionId,
            int itemNumber,
            String questionTitle,
            EvaluationArea evaluationArea,
            AssessmentQuestionTypeGroup questionTypeGroup,
            DifficultyBand difficultyBand,
            GradingStatus gradingStatus,
            StudentItemResultType resultType,
            BigDecimal score,
            BigDecimal maxScore,
            Long solvingDurationMs,
            Long classMedianSolvingDurationMs,
            int classCorrectStudentCount,
            int classGradedStudentCount,
            BigDecimal classAccuracyRate,
            List<AnswerUnitResult> answerUnits
    ) {
        public StudentItemResult {
            answerUnits = List.copyOf(answerUnits);
        }
    }

    public record AnswerUnitResult(
            Long answerUnitId,
            int displayOrder,
            String label,
            DiagnosticStage diagnosticStage,
            GradingStatus gradingStatus,
            String studentAnswer,
            String correctAnswer,
            BigDecimal score,
            StudentItemResultType resultType
    ) {
    }
}
