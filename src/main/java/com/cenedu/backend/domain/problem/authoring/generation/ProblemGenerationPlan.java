package com.cenedu.backend.domain.problem.authoring.generation;

import java.util.List;
import java.util.UUID;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;

/** 은행 문항과 AI 부족분을 최종 화면 순서로 합친 실행 계획이다. */
public record ProblemGenerationPlan(UUID clientRequestId, GenerationJobType jobType,
                                    List<ProblemGenerationSlotPlan> slots) {
    public ProblemGenerationPlan {
        if (clientRequestId == null || jobType == null || slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("생성 계획은 요청 ID, 유형, 슬롯이 필요합니다.");
        }
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) == null || slots.get(i).slotIndex() != i + 1) {
                throw new IllegalArgumentException("슬롯 순서는 1부터 연속이어야 합니다.");
            }
        }
        slots = List.copyOf(slots);
    }
}
