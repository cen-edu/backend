package com.cenedu.backend.domain.problem.dto.response;

import com.cenedu.backend.domain.problem.entity.enums.AuthoringInteractionStatus;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringLifecycleStatus;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationStatus;

/** 화면과 Worksheet 조율측이 Session의 진행·최종화 가능 상태를 확인한다. */
public record AuthoringSessionStatusResponse(
        Long sessionId,
        AuthoringLifecycleStatus lifecycleStatus,
        AuthoringOperationStatus operationStatus,
        AuthoringInteractionStatus interactionStatus,
        Long currentVersionId,
        Long pendingVersionId,
        boolean readyForFinalization,
        Long finalizedQuestionId,
        String errorCode
) {
}
