package com.cenedu.backend.domain.problem.authoring.generation;

import java.util.List;
import java.util.UUID;

import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;

/** 학습지 하나에서 부족한 여러 문항을 하나의 멱등 Job으로 묶는다. */
public record ProblemGenerationBatchCommand(
        UUID clientRequestId,
        GenerationJobType jobType,
        List<ProblemGenerationCommand> items
) {
}
