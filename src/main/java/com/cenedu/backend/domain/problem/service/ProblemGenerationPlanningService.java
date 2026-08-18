package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSlotSource;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationPlan;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationRequirement;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationSlotPlan;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult;
import org.springframework.stereotype.Service;

/** 문제은행을 먼저 채우고 부족한 슬롯만 AI 명령으로 만드는 계획을 계산한다. */
@Service
public class ProblemGenerationPlanningService {
    private final ProblemQuestionSelector selector;
    private final ProblemBankSnapshotQueryService snapshotQueryService;

    public ProblemGenerationPlanningService(ProblemQuestionSelector selector,
                                            ProblemBankSnapshotQueryService snapshotQueryService) {
        this.selector = selector;
        this.snapshotQueryService = snapshotQueryService;
    }

    /** 요청 조건을 화면 순서가 보존된 실행 계획으로 변환한다. */
    public ProblemGenerationPlan plan(UUID clientRequestId, GenerationJobType jobType,
                                      List<ProblemGenerationRequirement> requirements) {
        List<ProblemGenerationSlotPlan> slots = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();
        int index = 1;
        for (ProblemGenerationRequirement requirement : requirements) {
            List<ProblemQuestion> bank = selector.selectAvailable(requirement.subUnitId(),
                requirement.difficulty(), requirement.questionType(), Integer.MAX_VALUE, selectedIds);
            List<Long> candidateIds = bank.stream().map(ProblemQuestion::getId).toList();
            List<BankSnapshotResult> snapshotResults = snapshotQueryService.getSnapshots(candidateIds);
            selectedIds.addAll(candidateIds);
            java.util.Map<Long, BankSnapshotResult> resultById = snapshotResults.stream()
                    .collect(java.util.stream.Collectors.toMap(BankSnapshotResult::questionId, result -> result));
            int reusableCount = 0;
            for (ProblemQuestion question : bank) {
                BankSnapshotResult result = resultById.get(question.getId());
                if (result == null || !result.reusable()) continue;
                selectedIds.add(question.getId());
                reusableCount++;
                slots.add(new ProblemGenerationSlotPlan(index++, GenerationSlotSource.BANK_REUSE,
                    question.getId(), result.snapshot(), result.assetStorageKeys(), null));
                if (reusableCount == requirement.count()) break;
            }
            int shortage = requirement.count() - reusableCount;
            for (int i = 0; i < shortage; i++) {
                ProblemGenerationCommand command = new ProblemGenerationCommand(UUID.randomUUID(),
                    null, requirement.purpose(), requirement.specification(), requirement.curriculum(),
                    requirement.references(), requirement.conceptEvidence());
                slots.add(new ProblemGenerationSlotPlan(index++, GenerationSlotSource.AI_GENERATION,
                    null, command));
            }
        }
        return new ProblemGenerationPlan(clientRequestId, jobType, slots);
    }
}
