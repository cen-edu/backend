package com.cenedu.backend.domain.problem.authoring.visual;

import java.util.List;

/** 시각 자료 생성 정책을 위반한 semantic 후보를 설명한다. */
public final class VisualPolicyViolationException extends IllegalArgumentException {
    private final List<String> violations;

    public VisualPolicyViolationException(List<String> violations) {
        super(String.join("; ", List.copyOf(violations)));
        this.violations = List.copyOf(violations);
    }

    /** 정책 위반 사유를 구조화된 목록으로 반환한다. */
    public List<String> violations() {
        return violations;
    }
}
