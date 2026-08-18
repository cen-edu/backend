package com.cenedu.backend.domain.problem.dto.response;

import com.cenedu.backend.domain.problem.entity.enums.GenerationJobStatus;

/** 비동기 문제 생성 접수 직후 반환하는 최소 응답이다. */
public record ProblemGenerationStartResponse(Long jobId, GenerationJobStatus status, int totalCount) {
}
