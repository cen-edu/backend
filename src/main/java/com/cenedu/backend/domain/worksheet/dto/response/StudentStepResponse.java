package com.cenedu.backend.domain.worksheet.dto.response;

import java.util.List;

import com.cenedu.backend.domain.problem.entity.ProblemStep;

/**
 * 빈칸형 문항의 풀이 단계 하나. {@code format}이 {@code step}일 때만 값이 있다.
 *
 * <p>명세 예시엔 {@code instruction} 필드가 있지만 {@code problem_step} 엔티티에 그런 컬럼이 없다
 * (명세와 실제 스키마 불일치, 확인 완료). 프론트({@code PracticeProblemView.jsx}) 소비 지점이
 * {@code step.instruction ?? '기본 문구'}로 부재를 이미 감당하므로 필드를 넣지 않는다 — 없는
 * 데이터를 지어내지 않는다.
 */
public record StudentStepResponse(Long stepId, String label, List<StudentSegmentResponse> segments) {

    public static StudentStepResponse from(ProblemStep step, List<StudentSegmentResponse> segments) {
        return new StudentStepResponse(step.getId(), step.getLabel(), List.copyOf(segments));
    }
}
