package com.cenedu.backend.domain.problem.dto.request;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** 기존 동기 API와 분리된 일반학습 비동기 생성 요청이다. */
public record AsyncProblemGenerationRequest(
        @NotNull UUID clientRequestId,
        @NotEmpty List<@NotNull @Valid ProblemGenerationItemRequest> items) {
}
