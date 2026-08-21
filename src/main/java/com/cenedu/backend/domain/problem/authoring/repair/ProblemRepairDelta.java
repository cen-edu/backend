package com.cenedu.backend.domain.problem.authoring.repair;

import java.util.Map;

/** Repair LLM이 반환하는 수정 대상 필드만의 대체값이다. */
public record ProblemRepairDelta(
        Map<RepairTarget, Object> replacements,
        String rationale
) {
    public ProblemRepairDelta {
        replacements = replacements == null ? Map.of() : Map.copyOf(replacements);
    }
}
