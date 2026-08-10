package com.cenedu.backend.domain.analysis.dto;

import com.cenedu.backend.domain.analysis.entity.LearningStatus;
import com.cenedu.backend.domain.analysis.service.LearningStepCatalog;
import com.cenedu.backend.global.common.enums.DisplayLabels;

/** 보고서와 LLM 에 넘길 단순한 결과 한 줄. */
public record LearningReportItem(
        String conceptName,
        String stepName,
        String status,
        String statusName,
        int errorCount,
        String nextAction
) {
    public static LearningReportItem from(
            LearningState state,
            LearningStepCatalog.LearningStep step
    ) {
        if (!state.conceptId().equals(step.conceptId())
                || !state.stepId().equals(step.stepId())) {
            throw new IllegalArgumentException("상태와 학습 단계가 서로 다릅니다.");
        }
        String status = statusCode(state.status());
        return new LearningReportItem(
                step.conceptName(),
                step.stepName(),
                status,
                DisplayLabels.status(status),
                state.errorCount(),
                step.nextAction()
        );
    }

    /**
     * 프론트엔드와 같은 코드를 쓴다. 한글 표기는 DisplayLabels 가 붙인다.
     *
     * <p>CLEAR 와 IMPROVED 는 둘 다 stable 이다. 화면에서는 "확인 기준을 충족했다"는 뜻이 같고,
     * 그 자리에 어떻게 도달했는지는 상태값이 아니라 오류 수로 읽는다.
     */
    private static String statusCode(LearningStatus status) {
        return switch (status) {
            case CLEAR, IMPROVED -> "stable";
            case WATCH -> "review";
            case NEEDS_SUPPORT -> "priority";
        };
    }
}
