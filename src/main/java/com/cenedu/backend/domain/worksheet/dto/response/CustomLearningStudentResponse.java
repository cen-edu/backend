package com.cenedu.backend.domain.worksheet.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 맞춤 학습 카드 안의 학생 행.
 *
 * <p>필드는 {@link LearningStatusStudentResponse}와 같고 {@code assignmentId} 하나만 늘었다 —
 * 같은 화면에서 원본 학습지 표와 맞춤 표가 나란히 뜨므로 프론트가 표 컴포넌트를 재사용해야 한다.
 * 그쪽 DTO에 필드를 더하지 않은 이유는 기존 {@code /{assignmentId}/students} 계약이 함께
 * 바뀌기 때문이다. 그 화면은 카드가 곧 배정이라 {@code assignmentId}가 잉여다.
 *
 * <p>맞춤 배정은 학생 한 명씩이라 {@code assignmentId}가 학생마다 다르다.
 */
public record CustomLearningStudentResponse(

        @Schema(description = "이 학생의 맞춤 배정 ID. 학생마다 다르다")
        Long assignmentId,

        Long assignmentStudentId,
        Long studentId,

        @Schema(description = "원본 배정 명단 기준 표시 순번. 맞춤 대상은 명단의 일부라 "
                + "1,4,6 처럼 띄엄띄엄 나오는 것이 정상이다")
        int displayNumber,

        @Schema(nullable = true)
        String name,

        @Schema(allowableValues = {"not-started", "in-progress", "submitted", "not-submitted"})
        String status,

        short doneUnits,

        @Schema(description = "채점 상태. 일반 학습은 항상 null",
                allowableValues = {"pending", "done"}, nullable = true)
        String grading,

        @Schema(nullable = true)
        BigDecimal score,

        @Schema(nullable = true)
        OffsetDateTime submittedAt
) {

    /** 학생 행 파생은 {@link LearningStatusStudentResponse}가 하고 여기서는 배정 ID만 덧붙인다. */
    public static CustomLearningStudentResponse of(
            Long assignmentId, LearningStatusStudentResponse student
    ) {
        return new CustomLearningStudentResponse(
                assignmentId,
                student.assignmentStudentId(),
                student.studentId(),
                student.displayNumber(),
                student.name(),
                student.status(),
                student.doneUnits(),
                student.grading(),
                student.score(),
                student.submittedAt());
    }
}
