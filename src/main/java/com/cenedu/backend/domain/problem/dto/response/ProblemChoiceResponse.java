package com.cenedu.backend.domain.problem.dto.response;

import com.cenedu.backend.domain.problem.entity.ProblemChoice;

public record ProblemChoiceResponse(
    Long id,
    short displayOrder,
    String content
) {

    /**
     * 객관식 보기 엔티티를 상세 응답으로 변환한다.
     */
    public static ProblemChoiceResponse from(
        ProblemChoice choice
    ) {
        return new ProblemChoiceResponse(
            choice.getId(),
            choice.getDisplayOrder(),
            choice.getContent()
        );
    }
}
