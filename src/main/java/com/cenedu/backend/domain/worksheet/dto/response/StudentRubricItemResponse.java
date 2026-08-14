package com.cenedu.backend.domain.worksheet.dto.response;

import com.cenedu.backend.domain.problem.entity.ProblemRubricItem;

/** 서술형 채점 기준 항목 하나의 판정. {@code evidence}(LLM 판정 근거)는 절대 담지 않는다(명세 8절). */
public record StudentRubricItemResponse(Long rubricItemId, String description, short weight, boolean satisfied) {

    public static StudentRubricItemResponse from(ProblemRubricItem rubricItem, boolean satisfied) {
        return new StudentRubricItemResponse(
                rubricItem.getId(), rubricItem.getLabel(), rubricItem.getWeight(), satisfied);
    }
}
