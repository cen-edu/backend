package com.cenedu.backend.domain.problem.dto.response;

import java.util.List;

import com.cenedu.backend.domain.problem.entity.ProblemStep;

public record ProblemStepResponse(
    Long id,
    short displayOrder,
    String label,
    List<ProblemStepSegmentResponse> segments
) {

    /**
     * 풀이 단계 엔티티와 파싱된 세그먼트를 단계 상세 응답으로 변환한다.
     */
    public static ProblemStepResponse from(
        ProblemStep step,
        List<ProblemStepSegmentResponse> segments
    ) {
        return new ProblemStepResponse(
            step.getId(),
            step.getDisplayOrder(),
            step.getLabel(),
            List.copyOf(segments)
        );
    }
}
