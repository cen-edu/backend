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
import org.springframework.stereotype.Service;

/** 문제은행을 먼저 채우고 부족한 슬롯만 AI 명령으로 만드는 계획을 계산한다. */
@Service
public class ProblemGenerationPlanningService {
    private final ProblemQuestionSelector selector;

    public ProblemGenerationPlanningService(ProblemQuestionSelector selector) {
        this.selector = selector;
    }

    /** 요청 조건을 화면 순서가 보존된 실행 계획으로 변환한다. */
    public ProblemGenerationPlan plan(UUID clientRequestId, GenerationJobType jobType,
                                      List<ProblemGenerationRequirement> requirements) {
        List<ProblemGenerationSlotPlan> slots = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();
        int index = 1;
        for (ProblemGenerationRequirement requirement : requirements) {
            List<ProblemQuestion> bank = selector.selectAvailable(requirement.subUnitId(),
                requirement.difficulty(), requirement.questionType(), requirement.count(), selectedIds);
            for (ProblemQuestion question : bank) {
                selectedIds.add(question.getId());
                slots.add(new ProblemGenerationSlotPlan(index++, GenerationSlotSource.BANK_REUSE,
                    question.getId(), null));
            }
            int shortage = requirement.count() - bank.size();
            for (int i = 0; i < shortage; i++) {
                ProblemGenerationCommand command = new ProblemGenerationCommand(UUID.randomUUID(),
                    requirement.purpose(), requirement.specification(), requirement.curriculumContext(),
                    requirement.references(), requirement.conceptEvidence());
                slots.add(new ProblemGenerationSlotPlan(index++, GenerationSlotSource.AI_GENERATION,
                    null, command));
            }
        }
        return new ProblemGenerationPlan(clientRequestId, jobType, slots);
    }
}
