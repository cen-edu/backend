package com.cenedu.backend.ai.verification.adapter;

enum AnswerMismatchCause {
    NONE,
    AUTHORING_ANSWER_WRONG,
    SOLVER_UNCERTAIN,
    QUESTION_AMBIGUOUS,
    EXPLANATION_INCONSISTENT
}
