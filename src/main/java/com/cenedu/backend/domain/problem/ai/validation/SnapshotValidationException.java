package com.cenedu.backend.domain.problem.ai.validation;

import java.util.List;

/** 문항 스냅샷의 구조 또는 정규화 규칙 위반을 모두 담아 전달하는 예외다. */
public final class SnapshotValidationException extends RuntimeException {

    private final List<String> violations;

    /** 검증 위반 목록을 변경 불가능한 형태로 보존한다. */
    public SnapshotValidationException(List<String> violations) {
        super("문항 스냅샷 검증 실패: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    /** 재시도 프롬프트와 로그에 사용할 검증 위반 목록을 반환한다. */
    public List<String> violations() {
        return violations;
    }
}
