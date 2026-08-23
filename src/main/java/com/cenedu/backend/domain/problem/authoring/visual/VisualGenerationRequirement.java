package com.cenedu.backend.domain.problem.authoring.visual;

/** 문제 생성이 요구하는 시각 자료 생성 방식과 원본 유형을 담는다. */
public record VisualGenerationRequirement(
        VisualGenerationMode mode,
        VisualReferenceKind requiredKind
) {
    public VisualGenerationRequirement {
        if (mode == null) throw new IllegalArgumentException("visual mode가 필요합니다.");
        if (mode == VisualGenerationMode.PRESERVE_ORIGIN
                && (requiredKind == null
                || requiredKind == VisualReferenceKind.NONE
                || requiredKind == VisualReferenceKind.UNKNOWN_FIGURE)) {
            throw new IllegalArgumentException("보존할 origin visual kind가 필요합니다.");
        }
        if (mode != VisualGenerationMode.PRESERVE_ORIGIN && requiredKind == null) {
            requiredKind = VisualReferenceKind.NONE;
        }
    }

    /** 시각 자료를 만들지 않는 기본 생성 요구를 반환한다. */
    public static VisualGenerationRequirement none() {
        return new VisualGenerationRequirement(VisualGenerationMode.NONE, VisualReferenceKind.NONE);
    }
}
