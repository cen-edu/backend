package com.cenedu.backend.domain.grading.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 확정 결과(명세 10절). 이때부터 학생이 점수·정답·해설을 본다.
 *
 * @param releasedStudentCount 제출한 학생 수. <b>미제출자는 세지 않는다</b> — 공개할 결과가 없어
 *                             {@code released_at}을 채우지 않는다
 */
public record GradingReleaseResponse(
        Long assignmentId,

        @Schema(allowableValues = {"confirmed"})
        String status,

        OffsetDateTime releasedAt,
        int releasedStudentCount
) {
}
