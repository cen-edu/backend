package com.cenedu.backend.domain.problem.authoring.generation;

import com.cenedu.backend.domain.problem.entity.enums.GenerationItemStatus;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSlotSource;
import com.cenedu.backend.global.common.enums.CustomStage;

/** 화면이 문항 위치별 생성·검증 상태를 보여주는 조회 결과다. */
public record ProblemGenerationItemResult(
        Long itemId,
        Long sessionId,
        int itemOrder,
        GenerationItemStatus status,
        short retryCount,
        String errorCode,
        GenerationSlotSource source,
        Long sourceQuestionId,
        Long originQuestionId,
        CustomStage customStage
) {
    public ProblemGenerationItemResult(Long itemId, Long sessionId, int itemOrder,
                                       GenerationItemStatus status, short retryCount,
                                       String errorCode) {
        this(itemId, sessionId, itemOrder, status, retryCount, errorCode,
                null, null, null, null);
    }
}
