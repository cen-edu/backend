package com.cenedu.backend.domain.problem.authoring.snapshot;

import java.util.List;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;

/** 문제은행 문항의 스냅샷 변환·구조 검증 결과다. */
public record BankSnapshotResult(Long questionId, QuestionSnapshotV1 snapshot,
                                 boolean reusable, List<String> violations) {
    public BankSnapshotResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }
}
