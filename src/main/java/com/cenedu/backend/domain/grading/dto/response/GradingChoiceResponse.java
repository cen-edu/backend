package com.cenedu.backend.domain.grading.dto.response;

import com.cenedu.backend.domain.problem.entity.ProblemChoice;

/**
 * 객관식 보기 하나. 정답 여부는 담지 않는다 — 어느 보기가 정답인지는
 * {@link GradingAnswerUnitResponse#correctChoiceId()}가 가리킨다.
 *
 * <p>학생 API의 같은 이름 DTO를 재사용하지 않는다(명세 6절). 지금은 모양이 같지만 교사 화면에
 * 채점 정보가 붙을 때 두 응답이 같이 끌려가면 안 된다.
 */
public record GradingChoiceResponse(Long choiceId, short displayOrder, String text) {

    public static GradingChoiceResponse from(ProblemChoice choice) {
        return new GradingChoiceResponse(choice.getId(), choice.getDisplayOrder(), choice.getContent());
    }
}
