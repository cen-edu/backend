package com.cenedu.backend.domain.problem.authoring.repair;

import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;

/** 기존 Snapshot과 단일 묶음 Repair 계획을 시스템 수정 Port에 전달한다. */
public record ProblemRepairCommand(
        UUID repairRequestId,
        QuestionSnapshotV1 snapshot,
        ProblemRepairPlan plan
) {
}
