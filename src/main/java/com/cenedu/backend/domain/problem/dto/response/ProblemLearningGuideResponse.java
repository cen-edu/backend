package com.cenedu.backend.domain.problem.dto.response;

import java.util.List;

public record ProblemLearningGuideResponse(
    String conceptTitle,
    String summary,
    List<String> keyPoints
) {

    /**
     * 학습 가이드 표시 정보를 생성한다.
     */
    public static ProblemLearningGuideResponse of(
        String conceptTitle,
        String summary,
        List<String> keyPoints
    ) {
        return new ProblemLearningGuideResponse(
            conceptTitle,
            summary,
            List.copyOf(keyPoints)
        );
    }
}
