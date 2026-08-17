package com.cenedu.backend.domain.problem.authoring.generation;

/** Item Worker가 DB Entity 없이 문항 하나를 실행할 때 사용하는 내부 계약이다. */
public record ProblemGenerationWorkItem(
        Long itemId,
        Long jobId,
        Long ownerTeacherId,
        Long sessionId,
        ProblemGenerationCommand command
) {
}
