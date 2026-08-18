package com.cenedu.backend.domain.problem.dto.response;

/** 최종화된 문제를 Worksheet가 학생에게 배포할 수 있는지 나타내는 공개 상태다. */
public enum ProblemDeploymentStatus {
    READY,
    WAITING_FOR_ASSETS,
    BLOCKED_BY_ASSET_FAILURE;

    public boolean isDeployable() {
        return this == READY;
    }
}
