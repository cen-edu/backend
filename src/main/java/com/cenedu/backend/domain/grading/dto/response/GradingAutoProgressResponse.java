package com.cenedu.backend.domain.grading.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 자동채점 진행률(명세 8절).
 *
 * <p>세 카운트는 {@code grading_status}에서 직접 세므로 서버를 재시작해도 값이 유지된다.
 * {@code running}만 메모리 상태다 — <b>{@code running: false}인데 {@code remainingCount > 0}이면
 * 재시작으로 끊긴 것</b>이고, 교사가 다시 실행하면 남은 칸부터 이어진다.
 *
 * @param totalCount 이 배포에서 <b>제출한 학생 전원의 채점 칸 수</b>. 세 카운트의 합과 같다
 */
public record GradingAutoProgressResponse(

        @Schema(description = "이 배포에 진행 중인 작업이 있는지. 메모리 상태라 재시작하면 풀린다")
        boolean running,

        int totalCount,
        int gradedCount,
        int failedCount,
        int remainingCount
) {
}
