package com.cenedu.backend.ai.verification.adapter;

import java.util.Map;

record AnswerMismatchContext(boolean mismatch, Map<String, String> solverAnswers) {
    AnswerMismatchContext {
        solverAnswers = solverAnswers == null ? Map.of() : Map.copyOf(solverAnswers);
    }

    static AnswerMismatchContext none() { return new AnswerMismatchContext(false, Map.of()); }
}
