package com.cenedu.backend.domain.problem.authoring.repair;

import java.util.List;
import java.util.Set;

/** 검증 Finding을 한 번의 묶음 수정 요청으로 바꾼 결과다. */
public record ProblemRepairPlan(
        Set<RepairTarget> targets,
        List<String> reasons,
        boolean repairable
) {
    public ProblemRepairPlan {
        targets = targets == null ? Set.of() : Set.copyOf(targets);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static ProblemRepairPlan notRepairable(String reason) {
        return new ProblemRepairPlan(Set.of(), List.of(reason), false);
    }
}
