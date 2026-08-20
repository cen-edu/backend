package com.cenedu.backend.domain.problem.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 취약점 분석 결과를 기준으로 맞춤 문제 생성을 요청하는 HTTP 계약이다. */
public record CustomProblemGenerationRequest(
        @NotNull UUID clientRequestId,
        @NotNull @Positive Long sourceAssignmentId,
        @NotNull @Positive Long studentId,
        @NotEmpty @Size(max = 20) List<@NotNull @Valid CustomProblemGenerationItemRequest> items
) {
}
