package com.cenedu.backend.domain.analysis.dto;

import com.cenedu.backend.domain.analysis.entity.LearningStatus;

/**
 * 한 학생·개념·단계의 현재 상태.
 *
 * <p>status 와 세 집계는 진단 응답만으로 계산한다. 응용 응답은 세지만 상태를 움직이지 않으므로
 * 따로 담는다. 응용 정답률을 습득의 근거로 쓰지 않는다.
 */
public record LearningState(
        String learnerId,
        String conceptId,
        String stepId,
        LearningStatus status,
        int errorCount,
        int distinctErrorProblemCount,
        int consecutiveIndependentCorrectCount,
        int appliedAttemptCount,
        int appliedCorrectCount
) {
}
