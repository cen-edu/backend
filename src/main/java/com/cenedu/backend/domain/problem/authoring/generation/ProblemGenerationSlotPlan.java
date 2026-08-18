package com.cenedu.backend.domain.problem.authoring.generation;

/** 화면의 한 문항 위치에 어떤 공급원을 배치할지 표현한다. */
public record ProblemGenerationSlotPlan(
        int slotIndex,
        GenerationSlotSource source,
        Long sourceQuestionId,
        ProblemGenerationCommand generationCommand
) {
    public ProblemGenerationSlotPlan {
        if (slotIndex < 1 || source == null) throw new IllegalArgumentException("슬롯 정보가 올바르지 않습니다.");
        if (source == GenerationSlotSource.BANK_REUSE && (sourceQuestionId == null || generationCommand != null)) {
            throw new IllegalArgumentException("은행 재사용 슬롯은 원천 문항만 가져야 합니다.");
        }
        if (source == GenerationSlotSource.AI_GENERATION && (sourceQuestionId != null || generationCommand == null)) {
            throw new IllegalArgumentException("AI 슬롯은 생성 명령만 가져야 합니다.");
        }
    }
}
