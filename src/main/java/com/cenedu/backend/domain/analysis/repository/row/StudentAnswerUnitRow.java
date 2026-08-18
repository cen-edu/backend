package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;

import com.cenedu.backend.domain.analysis.entity.enums.StudentItemResultType;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;

/** 학생 문항 결과에 표시할 답안 단위별 응답과 정답. */
public record StudentAnswerUnitRow(
        Long worksheetItemId,
        Long answerUnitId,
        int displayOrder,
        String label,
        String diagnosticType,
        GradingStatus gradingStatus,
        String studentAnswer,
        String correctAnswer,
        BigDecimal score,
        StudentItemResultType resultType
) {
}
