package com.cenedu.backend.domain.problem.authoring.generation;

import java.util.List;

import com.cenedu.backend.domain.problem.entity.enums.GenerationJobStatus;

/** Job 전체 상태와 독립적인 Item 결과를 요청 순서대로 반환한다. */
public record ProblemGenerationJobResult(
        Long jobId,
        GenerationJobStatus status,
        List<ProblemGenerationItemResult> items
) {
}
