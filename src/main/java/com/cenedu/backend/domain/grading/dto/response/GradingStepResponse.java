package com.cenedu.backend.domain.grading.dto.response;

import java.util.List;

import com.cenedu.backend.domain.problem.entity.ProblemStep;

/**
 * 빈칸형 문항의 풀이 단계 하나. {@code format}이 {@code step}일 때만 값이 있다.
 *
 * <p>학생 풀이 화면과 같은 구조를 내려보내, 교사가 학생이 본 것과 같은 배치로 채점하게 한다.
 * 이 구조가 없으면 프론트가 칸 목록으로 단계를 짐작해 그릴 수밖에 없다.
 */
public record GradingStepResponse(Long stepId, String label, List<GradingSegmentResponse> segments) {

    public static GradingStepResponse from(ProblemStep step, List<GradingSegmentResponse> segments) {
        return new GradingStepResponse(step.getId(), step.getLabel(), List.copyOf(segments));
    }
}
