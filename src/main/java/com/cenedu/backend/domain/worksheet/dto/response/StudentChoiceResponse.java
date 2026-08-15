package com.cenedu.backend.domain.worksheet.dto.response;

import com.cenedu.backend.domain.problem.entity.ProblemChoice;

/**
 * 객관식 보기 하나. 정답 여부는 절대 담지 않는다 — {@code ProblemChoice}는 애초에 그 컬럼이 없다
 * (설계상 의도, 확인 완료).
 */
public record StudentChoiceResponse(Long choiceId, short displayOrder, String text) {

    public static StudentChoiceResponse from(ProblemChoice choice) {
        return new StudentChoiceResponse(choice.getId(), choice.getDisplayOrder(), choice.getContent());
    }
}
