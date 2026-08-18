package com.cenedu.backend.domain.worksheet.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignmentStudent;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 학생별 진행 행.
 *
 * <p>{@code status}는 {@link StudentResponseFormatter}가 판정한다 — 규칙을 복사하면 학습 현황과
 * 학생 화면이 같은 학생을 다르게 보여준다.
 */
public record LearningStatusStudentResponse(
        Long assignmentStudentId,
        Long studentId,

        @Schema(description = "이 배포 명단 안의 표시 순번. 출석번호가 아니며 다른 배포와 값이 다르다")
        int displayNumber,

        @Schema(description = "학생 이름. 배포 후 담당이 바뀐 학생은 null", nullable = true)
        String name,

        @Schema(description = "학생 진행 상태",
                allowableValues = {"not-started", "in-progress", "submitted", "not-submitted"})
        String status,

        @Schema(description = "푼 칸 수. 진행률 분자이며 totalUnits 와 축이 같다")
        short doneUnits,

        @Schema(description = "채점 상태. 일반 학습은 항상 null",
                allowableValues = {"pending", "done"}, nullable = true)
        String grading,

        @Schema(description = "총점. 일반 학습은 항상 null", nullable = true)
        BigDecimal score,

        @Schema(description = "제출 일시. 미제출이면 null", nullable = true)
        OffsetDateTime submittedAt
) {

    public static LearningStatusStudentResponse from(
            WorksheetAssignmentStudent was, WorksheetType type, OffsetDateTime dueAt,
            String name, int displayNumber
    ) {
        boolean assessment = type == WorksheetType.COMPREHENSIVE_ASSESSMENT;
        return new LearningStatusStudentResponse(
                was.getId(),
                was.getStudentId(),
                displayNumber,
                name,
                StudentResponseFormatter.toApiStatus(was.getStatus(), was.getProgressCount(), dueAt),
                was.getProgressCount(),
                assessment ? grading(was) : null,
                assessment ? was.getTotalScore() : null,
                was.getSubmittedAt());
    }

    /**
     * 채점 상태. {@code released_at}은 보지 않는다 — 그건 학생 공개 여부이고 교사는 공개 전에도
     * 점수를 본다. {@code done} 판정을 {@code graded_at}으로 하는 이유는 재채점 시 상태보다
     * 시각이 먼저 갱신될 수 있어서다.
     */
    private static String grading(WorksheetAssignmentStudent was) {
        if (was.getGradedAt() != null) {
            return "done";
        }
        return was.getSubmittedAt() == null ? null : "pending";
    }
}
