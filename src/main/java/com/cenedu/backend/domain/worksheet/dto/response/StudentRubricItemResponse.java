package com.cenedu.backend.domain.worksheet.dto.response;

import com.cenedu.backend.domain.problem.entity.ProblemRubricItem;

/**
 * 서술형 채점 기준 항목 하나의 판정. {@code evidence}(LLM 판정 근거)는 절대 담지 않는다(명세 8절).
 *
 * <p>{@code satisfied}는 <b>3상태</b>다. {@code null}이면 아직 판정하지 않은 것이고 {@code false}는
 * 보고서 미충족으로 판정한 것이다. 이 둘을 합치면 채점 전 답안이 "전부 틀린 답안"으로 보인다.
 */
public record StudentRubricItemResponse(Long rubricItemId, String description, short weight,
                                        Boolean satisfied) {

    public static StudentRubricItemResponse from(ProblemRubricItem rubricItem, Boolean satisfied) {
        return new StudentRubricItemResponse(
                rubricItem.getId(), rubricItem.getLabel(), rubricItem.getWeight(), satisfied);
    }
}
