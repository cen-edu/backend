package com.cenedu.backend.domain.grading.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 원본 배정 하나에서 파생된 맞춤 학습 묶음. <b>학생 → 차수</b> 2단이다.
 *
 * <p>맞춤 배정은 학생 한 명씩이라 원본 행처럼 인원 카운트만 내리면 화면에 아무것도 못 그린다 —
 * 원본은 카운트가 반 전체를 대표하지만 맞춤은 그렇지 않다. 그래서 여기만 학생 행이 목록에 들어온다.
 *
 * <p>집계 3값은 트리를 만든 뒤 <b>메모리에서 센다</b>. SQL 집계로 따로 내면 요약과 표가 어긋나고,
 * 어긋나는 순간 어느 쪽이 맞는지 알 수 없다.
 */
public record GradingCustomLearningResponse(

        @Schema(description = "맞춤을 받은 실인원. 한 학생이 여러 차수를 받아도 1로 센다")
        int studentCount,

        @Schema(description = "맞춤 배정 총 건수. students[].sessions 길이의 합과 같다")
        int sessionCount,

        @Schema(description = "가장 많이 받은 학생의 차수. 화면 헤더의 '최대 N차'")
        int maxSessionNumber,

        List<GradingCustomStudentResponse> students
) {

    public static GradingCustomLearningResponse of(List<GradingCustomStudentResponse> students) {
        int sessionCount = students.stream()
                .mapToInt(GradingCustomStudentResponse::sessionCount)
                .sum();
        int maxSessionNumber = students.stream()
                .flatMap(student -> student.sessions().stream())
                .mapToInt(GradingCustomSessionResponse::sessionNumber)
                .max()
                .orElse(0);
        return new GradingCustomLearningResponse(
                students.size(), sessionCount, maxSessionNumber, List.copyOf(students));
    }
}
