package com.cenedu.backend.domain.problem.authoring.generation;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import java.util.Map;

/** 화면의 한 문항 위치에 어떤 공급원을 배치할지 표현한다. */
public record ProblemGenerationSlotPlan(
        int slotIndex,
        GenerationSlotSource source,
        Long sourceQuestionId,
        QuestionSnapshotV1 sourceSnapshot,
        Map<String, String> sourceAssetStorageKeys,
        ProblemGenerationCommand generationCommand
) {
    public ProblemGenerationSlotPlan(int slotIndex, GenerationSlotSource source,
                                     Long sourceQuestionId,
                                     ProblemGenerationCommand generationCommand) {
        this(slotIndex, source, sourceQuestionId, null, Map.of(), generationCommand);
    }
    public ProblemGenerationSlotPlan(int slotIndex, GenerationSlotSource source,
                                     Long sourceQuestionId, QuestionSnapshotV1 sourceSnapshot,
                                     ProblemGenerationCommand generationCommand) {
        this(slotIndex, source, sourceQuestionId, sourceSnapshot, Map.of(), generationCommand);
    }
    public ProblemGenerationSlotPlan {
        if (slotIndex < 1 || source == null) throw new IllegalArgumentException("슬롯 정보가 올바르지 않습니다.");
        sourceAssetStorageKeys = sourceAssetStorageKeys == null ? Map.of() : Map.copyOf(sourceAssetStorageKeys);
        if (source == GenerationSlotSource.BANK_REUSE
                && (sourceQuestionId == null || sourceSnapshot == null || generationCommand != null)) {
            throw new IllegalArgumentException("은행 재사용 슬롯은 원천 문항과 검증된 스냅샷이 필요합니다.");
        }
        if (source == GenerationSlotSource.BANK_REUSE && sourceSnapshot != null
                && sourceSnapshot.metadata() == null) {
            throw new IllegalArgumentException("은행 재사용 스냅샷이 올바르지 않습니다.");
        }
        if (source == GenerationSlotSource.AI_GENERATION
                && (sourceQuestionId != null || sourceSnapshot != null
                || !sourceAssetStorageKeys.isEmpty() || generationCommand == null)) {
            throw new IllegalArgumentException("AI 슬롯은 생성 명령만 가져야 합니다.");
        }
    }
}
