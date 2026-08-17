package com.cenedu.backend.domain.grading.dto.response;

import com.cenedu.backend.domain.problem.dto.response.ProblemContentBlockResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 발문 렌더링 블록 하나. {@code prompt_text}는 담지 않는다 — 컬럼 주석이 화면 표시 금지다.
 *
 * <p>학생 API의 같은 이름 DTO를 재사용하지 않는다(명세 6절). 지금은 모양이 같지만 교사 화면에
 * 정답·채점 정보가 붙을 때 두 응답이 같이 끌려가면 안 된다.
 */
public record GradingContentBlockResponse(
        String blockId,

        @Schema(description = "DB 원값 그대로", allowableValues = {"TEXT", "FIGURE", "TABLE"})
        String blockKind,

        int displayOrder,
        String text,
        String assetRef,
        String imageUrl,
        String markup
) {

    public static GradingContentBlockResponse from(ProblemContentBlockResponse block, long questionId) {
        String imageUrl = block.assetRef() == null
                ? null
                : "/api/images/problems/%d/assets/%s".formatted(questionId, block.assetRef());
        return new GradingContentBlockResponse(
                block.blockId(),
                block.blockKind(),
                block.displayOrder(),
                block.text(),
                block.assetRef(),
                imageUrl,
                block.markup());
    }
}
