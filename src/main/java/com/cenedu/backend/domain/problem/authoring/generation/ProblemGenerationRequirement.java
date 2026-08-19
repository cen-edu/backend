package com.cenedu.backend.domain.problem.authoring.generation;

import java.util.List;
import com.cenedu.backend.global.common.enums.QuestionType;

/** 한 조건에서 은행 우선·부족분 AI 생성을 계산하기 위한 입력이다. */
public record ProblemGenerationRequirement(Long subUnitId, short difficulty,
                                           QuestionType questionType, int count,
                                           GenerationPurpose purpose,
                                           GenerationSpecification specification,
                                           CurriculumScope curriculum,
                                           List<GenerationReference> references,
                                           List<GenerationConceptEvidence> conceptEvidence) {
    public ProblemGenerationRequirement {
        if (subUnitId == null || count < 1 || purpose == null || specification == null
                || curriculum == null) throw new IllegalArgumentException("생성 조건이 올바르지 않습니다.");
        references = references == null ? List.of() : List.copyOf(references);
        conceptEvidence = conceptEvidence == null ? List.of() : List.copyOf(conceptEvidence);
    }
}
