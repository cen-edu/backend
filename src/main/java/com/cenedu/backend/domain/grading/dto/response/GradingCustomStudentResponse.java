package com.cenedu.backend.domain.grading.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 맞춤 학습 묶음 안의 학생 한 명. 받은 차수가 {@code sessions}에 오름차순으로 담긴다. */
public record GradingCustomStudentResponse(

        Long studentId,

        @Schema(description = "원본 배정 명단 기준 표시 순번. 맞춤 대상은 명단의 일부라 "
                + "1,4,6 처럼 띄엄띄엄 나오는 것이 정상이다")
        int displayNumber,

        @Schema(description = "학생 이름. 배포 후 담당이 바뀐 학생은 null", nullable = true)
        String name,

        int sessionCount,
        List<GradingCustomSessionResponse> sessions
) {

    public static GradingCustomStudentResponse of(
            Long studentId, int displayNumber, String name,
            List<GradingCustomSessionResponse> sessions
    ) {
        return new GradingCustomStudentResponse(
                studentId, displayNumber, name, sessions.size(), List.copyOf(sessions));
    }
}
