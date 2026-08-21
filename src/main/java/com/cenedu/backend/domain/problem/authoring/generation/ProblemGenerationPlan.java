package com.cenedu.backend.domain.problem.authoring.generation;

import java.util.List;
import java.util.UUID;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.global.common.enums.CustomStage;

/** 은행 문항과 AI 부족분을 최종 화면 순서로 합친 실행 계획이다. */
public record ProblemGenerationPlan(UUID clientRequestId, GenerationJobType jobType,
                                    List<ProblemGenerationSlotPlan> slots) {
    public ProblemGenerationPlan {
        if (clientRequestId == null || jobType == null || slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("생성 계획은 요청 ID, 유형, 슬롯이 필요합니다.");
        }
        for (int i = 0; i < slots.size(); i++) {
            ProblemGenerationSlotPlan slot = slots.get(i);
            if (slot == null || slot.slotIndex() != i + 1) {
                throw new IllegalArgumentException("슬롯 순서는 1부터 연속이어야 합니다.");
            }
            validateStageMetadata(jobType, slot);
        }
        slots = List.copyOf(slots);
    }

    private static void validateStageMetadata(GenerationJobType jobType, ProblemGenerationSlotPlan slot) {
        if (jobType != GenerationJobType.PERSONALIZED) {
            if (slot.customStage() != null || slot.originQuestionId() != null) {
                throw new IllegalArgumentException("일반·종합평가 슬롯에는 맞춤 단계 정보가 없어야 합니다.");
            }
            return;
        }
        CustomStage stage = slot.customStage();
        if (stage == null) {
            throw new IllegalArgumentException("맞춤 슬롯에는 customStage가 필요합니다.");
        }
        if (stage == CustomStage.REVIEW) {
            if (slot.source() != GenerationSlotSource.BANK_REUSE
                    || slot.sourceQuestionId() == null || slot.originQuestionId() != null) {
                throw new IllegalArgumentException("REVIEW 슬롯은 원문항 은행 재사용이어야 합니다.");
            }
        } else if (stage == CustomStage.SIMILAR) {
            boolean validBankReuse = slot.source() == GenerationSlotSource.BANK_REUSE
                    && slot.sourceQuestionId() != null && slot.originQuestionId() == null;
            boolean validAiGeneration = slot.source() == GenerationSlotSource.AI_GENERATION
                    && slot.originQuestionId() != null;
            if (!validBankReuse && !validAiGeneration) {
                throw new IllegalArgumentException("SIMILAR 슬롯은 은행 재사용 또는 기준 문항 기반 AI 생성이어야 합니다.");
            }
        } else if (stage == CustomStage.ADVANCED
                && (slot.source() != GenerationSlotSource.AI_GENERATION
                || slot.originQuestionId() == null)) {
            throw new IllegalArgumentException("ADVANCED 슬롯은 기준 문항 기반 AI 생성이어야 합니다.");
        }
    }
}
