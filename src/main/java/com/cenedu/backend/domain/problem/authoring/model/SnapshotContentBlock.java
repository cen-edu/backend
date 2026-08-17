package com.cenedu.backend.domain.problem.authoring.model;

/**
 * 학생 화면에 순서대로 렌더링할 발문·그림·표 블록이다.
 *
 * <p>{@code TEXT}는 {@code text}, {@code FIGURE}는 {@code assetRef}, {@code TABLE}은
 * {@code markup}만 사용한다. {@code blockKey}는 DB ID가 아닌 버전 간 안정적인 논리 키다.
 */
public record SnapshotContentBlock(
        String blockKey,
        SnapshotBlockKind blockKind,
        int displayOrder,
        String text,
        String assetRef,
        String markup
) {
}
