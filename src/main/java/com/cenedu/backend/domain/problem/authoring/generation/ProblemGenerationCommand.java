package com.cenedu.backend.domain.problem.authoring.generation;

import java.util.List;
import java.util.UUID;

/** 서버 재시작 후에도 재실행할 수 있도록 문항 하나의 전체 생성 입력을 담는다. */
public record ProblemGenerationCommand(
        UUID requestId,
        GenerationPurpose purpose,
        GenerationSpecification specification,
        CurriculumContext curriculumContext,
        List<GenerationReference> references,
        List<GenerationConceptEvidence> conceptEvidence
) {
}
