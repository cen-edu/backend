package com.cenedu.backend.domain.problem.dto.response;

import java.util.List;

import com.cenedu.backend.domain.problem.entity.enums.GenerationJobStatus;

/** 생성 Job 전체 상태와 문항별 슬롯을 함께 반환하는 polling 응답이다. */
public record ProblemGenerationJobStatusResponse(
        Long jobId,
        GenerationJobStatus status,
        int totalCount,
        int completedCount,
        List<ProblemGenerationSlotResponse> slots
) {

    public ProblemGenerationJobStatusResponse {
        if (jobId == null || jobId <= 0) {
            throw new IllegalArgumentException("jobId는 1 이상이어야 합니다.");
        }
        if (status == null) {
            throw new IllegalArgumentException("status는 필수입니다.");
        }
        if (totalCount < 0 || completedCount < 0 || completedCount > totalCount) {
            throw new IllegalArgumentException("Job 문항 집계 값이 올바르지 않습니다.");
        }
        slots = slots == null ? List.of() : List.copyOf(slots);
        if (slots.size() != totalCount) {
            throw new IllegalArgumentException("slots 수가 totalCount와 일치해야 합니다.");
        }
    }
}
