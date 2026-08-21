package com.cenedu.backend.ai.verification.adapter;

import java.util.List;

record OriginalInspectionResult(List<OriginalDefect> defects, AnswerMismatchCause answerMismatchCause) {
    OriginalInspectionResult {
        defects = defects == null ? List.of() : List.copyOf(defects);
        answerMismatchCause = answerMismatchCause == null ? AnswerMismatchCause.NONE : answerMismatchCause;
    }
}
