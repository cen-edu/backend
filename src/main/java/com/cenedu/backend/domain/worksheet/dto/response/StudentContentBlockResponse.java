package com.cenedu.backend.domain.worksheet.dto.response;

import com.cenedu.backend.domain.problem.dto.response.ProblemContentBlockResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 발문 렌더링 블록 하나. {@code prompt_text}는 절대 담지 않는다 — 정본은 {@code content_blocks}다.
 *
 * <p>{@link ProblemContentBlockResponse}(problem 도메인, 정답·설명 없이 이미 안전한 형태)를 그대로
 * 파싱 대상으로 재사용하고, 여기서는 {@code imageUrl} 조립만 더한다.
 */
public record StudentContentBlockResponse(
        String blockId,

        @Schema(description = "블록 종류. DB 원값 그대로(변환 없음)", allowableValues = {"TEXT", "FIGURE", "TABLE"})
        String blockKind,

        int displayOrder,
        String text,
        String assetRef,
        String imageUrl,
        String markup
) {

    public static StudentContentBlockResponse from(ProblemContentBlockResponse block, long questionId) {
        String imageUrl = block.assetRef() == null
                ? null
                : StudentResponseFormatter.assembleImageUrl(questionId, block.assetRef());
        return new StudentContentBlockResponse(
                block.blockId(),
                block.blockKind(),
                block.displayOrder(),
                block.text(),
                block.assetRef(),
                imageUrl,
                block.markup()
        );
    }
}
