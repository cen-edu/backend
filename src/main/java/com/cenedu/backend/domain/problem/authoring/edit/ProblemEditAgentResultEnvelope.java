package com.cenedu.backend.domain.problem.authoring.edit;

import java.util.Objects;

/** PROBLEM_EDIT Agent가 반환하는 구조화된 한 턴 결과의 외부 envelope다. */
public record ProblemEditAgentResultEnvelope(
        int schemaVersion,
        ProblemEditConversationResult problemEditResult
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String RESPONSE_KEY = "problemEditResult";

    public ProblemEditAgentResultEnvelope {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("지원하지 않는 문제 수정 응답 버전입니다.");
        }
        Objects.requireNonNull(problemEditResult, "problemEditResult");
    }
}
