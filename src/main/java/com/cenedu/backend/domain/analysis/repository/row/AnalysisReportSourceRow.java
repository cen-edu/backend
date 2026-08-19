package com.cenedu.backend.domain.analysis.repository.row;

import java.time.OffsetDateTime;

/** 보고서 생성 전제조건과 재생성 판단에 쓰는 학생 수행 회차 정보. */
public record AnalysisReportSourceRow(
        Long assignmentStudentId,
        String assignmentStatus,
        OffsetDateTime gradedAt,
        OffsetDateTime lastOverriddenAt
) {

    /** 채점이 완료되어 AI 문장을 만들 수 있는 상태인지 확인한다. */
    public boolean isGraded() {
        return "GRADED".equals(assignmentStatus);
    }

    /**
     * 채점 결과가 마지막으로 바뀐 시각. 자동·교사 채점 확정과 개별 점수 수정 중 나중 것을 고른다.
     *
     * <p>교사가 개별 문항 점수만 고치면 {@code graded_at} 은 움직이지 않고
     * {@code overridden_at} 만 갱신되므로 둘을 함께 봐야 한다.
     */
    public OffsetDateTime lastGradingChangedAt() {
        if (gradedAt == null) {
            return lastOverriddenAt;
        }
        if (lastOverriddenAt == null) {
            return gradedAt;
        }
        return lastOverriddenAt.isAfter(gradedAt) ? lastOverriddenAt : gradedAt;
    }
}
