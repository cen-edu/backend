package com.cenedu.backend.domain.worksheet.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.worksheet.repository.row.CustomLearningStudentCountRow;
import com.cenedu.backend.domain.worksheet.repository.row.CustomLearningWorksheetCountRow;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 원본 배정에 딸린 맞춤 학습 요약. 목록과 상세가 같이 쓴다.
 *
 * <p>{@code statusCounts}는 <b>네 값의 합이 배정 건수</b>가 되도록 학생 표와 같은 축으로 센다 —
 * 화면 상단 요약 카드(3값)와 달리 여기 숫자는 카드 안 표와 대조되어야 한다.
 */
public record CustomLearningSummaryResponse(

        @Schema(description = "맞춤 학습지 수. 화면 카드 수와 같다")
        int worksheetCount,

        @Schema(description = "맞춤을 받은 실인원. 한 학생이 여러 회차를 받아도 1로 센다")
        int studentCount,

        StatusCounts statusCounts,

        @Schema(description = "채점 집계. 맞춤 학습지가 전부 일반 학습이면 null — 채점 상태는 "
                + "종합평가에만 있어서 0으로 내리면 '아무도 채점 안 됨'으로 잘못 읽힌다",
                nullable = true)
        GradingCounts gradingCounts,

        @Schema(description = "가장 최근 맞춤 배정일", nullable = true)
        OffsetDateTime latestAssignedAt,

        @Schema(description = "가장 늦은 맞춤 마감일", nullable = true)
        OffsetDateTime latestDueAt
) {

    /** 진행 상태별 배정 건수(연인원). 학생 표의 {@code status} 값과 같은 규칙으로 센다. */
    public record StatusCounts(long notStarted, long inProgress, long submitted, long notSubmitted) {
    }

    /** 채점 상태별 배정 건수. 학생 행의 {@code grading}과 같은 규칙이다. */
    public record GradingCounts(long pending, long done) {
    }

    /** 목록 응답용. 집계 두 축을 합쳐 만든다. */
    public static CustomLearningSummaryResponse from(
            CustomLearningWorksheetCountRow worksheets, CustomLearningStudentCountRow students
    ) {
        boolean hasAssessment = worksheets.assessmentWorksheetCount() > 0;
        return new CustomLearningSummaryResponse(
                Math.toIntExact(worksheets.worksheetCount()),
                students == null ? 0 : Math.toIntExact(students.studentCount()),
                students == null
                        ? new StatusCounts(0, 0, 0, 0)
                        : new StatusCounts(students.notStarted(), students.inProgress(),
                                students.submitted(), students.notSubmitted()),
                hasAssessment && students != null
                        ? new GradingCounts(students.gradingPending(), students.gradingDone())
                        : null,
                worksheets.latestAssignedAt(),
                worksheets.latestDueAt());
    }

    /**
     * 상세 응답용. 이미 만든 학생 행을 그대로 센다 — 파생 규칙을 SQL로 복제하면 요약과 표가
     * 어긋날 수 있고, 어긋나는 순간 어느 쪽이 맞는지 알 수 없다.
     */
    public static CustomLearningSummaryResponse of(List<CustomLearningWorksheetResponse> worksheets) {
        List<CustomLearningStudentResponse> students = worksheets.stream()
                .flatMap(worksheet -> worksheet.students().stream())
                .toList();
        boolean hasGrading = worksheets.stream().anyMatch(worksheet -> "assessment".equals(worksheet.type()));
        return new CustomLearningSummaryResponse(
                worksheets.size(),
                Math.toIntExact(students.stream().map(CustomLearningStudentResponse::studentId).distinct().count()),
                new StatusCounts(
                        count(students, "not-started"),
                        count(students, "in-progress"),
                        count(students, "submitted"),
                        count(students, "not-submitted")),
                hasGrading
                        ? new GradingCounts(grading(students, "pending"), grading(students, "done"))
                        : null,
                worksheets.stream().map(CustomLearningWorksheetResponse::assignedAt)
                        .max(OffsetDateTime::compareTo).orElse(null),
                worksheets.stream().map(CustomLearningWorksheetResponse::dueAt)
                        .max(OffsetDateTime::compareTo).orElse(null));
    }

    private static long count(List<CustomLearningStudentResponse> students, String status) {
        return students.stream().filter(student -> status.equals(student.status())).count();
    }

    private static long grading(List<CustomLearningStudentResponse> students, String grading) {
        return students.stream().filter(student -> grading.equals(student.grading())).count();
    }
}
