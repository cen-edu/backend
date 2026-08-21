package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairDelta;
import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairPlan;
import com.cenedu.backend.domain.problem.authoring.repair.RepairTarget;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;

import tools.jackson.databind.ObjectMapper;

class ProblemRepairDeltaMergerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 허용된_해설만_교체하고_보호된_필드는_보존한다() {
        var base = ProblemSnapshotFixtures.shortInput();
        var plan = new ProblemRepairPlan(EnumSet.of(RepairTarget.EXPLANATION),
                List.of("해설 불일치"), true);
        var delta = new ProblemRepairDelta(java.util.Map.of(
                RepairTarget.EXPLANATION, mapper.valueToTree("수정된 해설")), "정답과 일치시킴");

        var merged = new ProblemRepairDeltaMerger(mapper).merge(base, plan, delta);

        assertThat(merged.explanation()).isEqualTo("수정된 해설");
        assertThat(merged.contentBlocks()).isEqualTo(base.contentBlocks());
        assertThat(merged.answerUnits()).isEqualTo(base.answerUnits());
    }
}
