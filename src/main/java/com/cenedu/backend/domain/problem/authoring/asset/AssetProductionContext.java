package com.cenedu.backend.domain.problem.authoring.asset;

import com.cenedu.backend.global.common.enums.QuestionType;

/** 임시 자산 경로를 문제 유형·Session·Version별로 안전하게 만들기 위한 서버 컨텍스트다. */
public record AssetProductionContext(
        Long sessionId,
        int versionNo,
        QuestionType questionType
) {
}
