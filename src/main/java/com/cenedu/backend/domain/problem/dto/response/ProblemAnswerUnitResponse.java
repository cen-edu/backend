package com.cenedu.backend.domain.problem.dto.response;

import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;
import com.cenedu.backend.domain.problem.entity.ProblemStep;
import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;
import com.cenedu.backend.global.common.enums.CompareMethod;

public record ProblemAnswerUnitResponse(
    Long id,
    Long stepId,
    String unitKey,
    int displayOrder,
    String label,
    String answer,
    CompareMethod compareMethod,
    DiagnosticType diagnosticType,
    String displayUnit
) {

    /**
     * 정답 단위 엔티티를 교사용 상세 응답으로 변환한다.
     */
    public static ProblemAnswerUnitResponse from(
        ProblemAnswerUnit answerUnit
    ) {
        ProblemStep step = answerUnit.getStep();

        return new ProblemAnswerUnitResponse(
            answerUnit.getId(),
            step == null ? null : step.getId(),
            answerUnit.getUnitKey(),
            answerUnit.getDisplayOrder(),
            answerUnit.getLabel(),
            answerUnit.getAnswerRaw(),
            answerUnit.getCompareMethod(),
            answerUnit.getDiagnosticType(),
            answerUnit.getDisplayUnit()
        );
    }
}
