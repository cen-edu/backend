package com.cenedu.backend.domain.worksheet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** 학습지에 담길 문항 한 줄. */
public record WorksheetItemRequest(
        @Schema(description = "기존 문제는 questionId, 생성·수정 문제는 sessionId를 사용합니다.")
        Long questionId,

        @Schema(description = "생성·수정·검증을 마친 문항 작성 Session ID")
        Long sessionId,

        @NotNull(message = "displayOrder는 필수입니다.")
        Integer displayOrder,

        @Schema(allowableValues = {"concept", "chat"})
        String supportMode,

        @Schema(allowableValues = {"retrace", "basic", "independent"})
        String customStage
) {

    /** 기존 문항과 작성 Session 중 하나만 참조하는지 검증한다. */
    @AssertTrue(message = "questionId와 sessionId 중 하나만 필요합니다.")
    public boolean isProblemReferenceValid() {
        return (questionId != null) != (sessionId != null);
    }
}
