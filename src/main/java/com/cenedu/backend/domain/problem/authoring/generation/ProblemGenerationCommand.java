package com.cenedu.backend.domain.problem.authoring.generation;

import java.util.List;
import java.util.UUID;

/** 서버 재시작 후에도 재실행할 수 있도록 문항 하나의 전체 생성 입력을 담는다. */
public record ProblemGenerationCommand(
        UUID requestId,
        UUID retrievalRequestId,
        GenerationPurpose purpose,
        GenerationSpecification specification,
        CurriculumScope curriculum,
        List<GenerationReference> references,
        List<GenerationConceptEvidence> conceptEvidence
) {
    public ProblemGenerationCommand {
        references = references == null ? List.of() : List.copyOf(references);
        conceptEvidence = conceptEvidence == null ? List.of() : List.copyOf(conceptEvidence);
    }
}
