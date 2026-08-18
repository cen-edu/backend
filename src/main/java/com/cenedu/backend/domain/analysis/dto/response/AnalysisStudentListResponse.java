package com.cenedu.backend.domain.analysis.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.entity.enums.AnalysisStatus;

/** 선택한 학습지의 학생별 분석 상태 목록. */
public record AnalysisStudentListResponse(
        List<StudentItem> students
) {
    public AnalysisStudentListResponse {
        students = List.copyOf(students);
    }

    public record StudentItem(
            Long studentId,
            String studentName,
            AnalysisStatus analysisStatus,
            BigDecimal performanceRate
    ) {
    }
}
