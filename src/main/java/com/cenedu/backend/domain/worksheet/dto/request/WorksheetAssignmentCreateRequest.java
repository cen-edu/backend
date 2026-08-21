package com.cenedu.backend.domain.worksheet.dto.request;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 학습지를 배포하는 요청.
 *
 * <p>대상은 반이거나 학생 한 명이고, 둘을 함께 줄 수 없다. {@code worksheet_assignment} 의
 * {@code ck_worksheet_assignment_target_xor} 제약이 같은 규칙을 DB 에서도 강제한다.
 *
 * <p>맞춤 학습지는 학생마다 취약점이 달라 반 전체 배포가 의미를 갖지 않는다. 맞춤 학습 분석
 * ({@code CustomLearningQueryRepository})도 학생 배정만 세션으로 인식한다.
 */
public record WorksheetAssignmentCreateRequest(
        @Schema(description = "배포 대상 반. studentId 와 함께 줄 수 없다")
        Long classId,

        @Schema(description = "배포 대상 학생. classId 와 함께 줄 수 없다")
        Long studentId,

        @NotNull(message = "dueAt은 필수입니다.")
        OffsetDateTime dueAt
) {

    @JsonIgnore
    @AssertTrue(message = "classId와 studentId 중 하나만 지정해야 합니다.")
    public boolean isExactlyOneTarget() {
        return (classId == null) != (studentId == null);
    }

    /** 학생 한 명에게 배포하는 요청인지 여부. */
    @JsonIgnore
    public boolean isStudentTarget() {
        return studentId != null;
    }
}
