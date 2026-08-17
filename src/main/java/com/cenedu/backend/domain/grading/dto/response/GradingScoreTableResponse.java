package com.cenedu.backend.domain.grading.dto.response;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 점수표(명세 5절). 종합평가와 일반학습이 응답 구조를 공유하고, 프론트가 {@code type}으로
 * 표시를 가른다 — 일반학습은 {@code maxScore}가 {@code null}이고 셀 점수가 0/1이다.
 */
public record GradingScoreTableResponse(
        Long assignmentId,
        String title,

        @Schema(allowableValues = {"practice", "assessment"})
        String type,

        String className,

        @Schema(allowableValues = {"grading", "graded", "confirmed"})
        String status,

        boolean modified,

        @Schema(description = "종합평가만 값이 있다")
        BigDecimal maxTotalScore,

        int studentCount,
        int submittedCount,
        Metrics metrics,
        List<GradingQuestionResponse> questions,
        List<GradingStudentRowResponse> students
) {

    /**
     * 요약 지표. 평균·최고·최저는 <b>채점이 끝난 학생만</b> 대상으로 한다 — 미채점 학생을 0점으로
     * 넣으면 채점이 진행될수록 평균이 올라가는 그래프가 된다.
     *
     * @param pendingCount 제출했는데 아직 {@code GRADED}가 아닌 학생 수. 확정 버튼 활성 조건이다
     */
    public record Metrics(
            BigDecimal average,
            BigDecimal highest,
            BigDecimal lowest,
            int pendingCount
    ) {
    }
}
