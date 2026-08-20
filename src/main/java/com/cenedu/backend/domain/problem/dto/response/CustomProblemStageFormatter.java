package com.cenedu.backend.domain.problem.dto.response;

import com.cenedu.backend.global.common.enums.CustomStage;

/** 맞춤 생성 단계를 polling 응답의 프론트 계약 문자열로 변환한다. */
public final class CustomProblemStageFormatter {
    private CustomProblemStageFormatter() {}

    /** 단계 enum을 소문자 응답 값으로 변환하고 일반 문항은 null을 반환한다. */
    public static String format(CustomStage stage) {
        return stage == null ? null : stage.name().toLowerCase(java.util.Locale.ROOT);
    }
}
