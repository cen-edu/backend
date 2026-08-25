package com.cenedu.backend.domain.problem.authoring.model;

/**
 * FIGURE 블록이 참조하는 이미지의 논리 정보다.
 *
 * <p>{@code altText}는 그림을 보지 못해도 같은 문제 정보를 얻을 수 있게 자산에 표시된 좌표·식·수치·
 * 보기 등을 설명한다. 따라서 그 값이 정답과 일치하더라도 실제 자산에 표시된 정보라면 허용한다.
 * 문제 본문과의 정합성 및 자산의 실제 내용과 다른 설명인지만 별도 검증한다.
 * 저장소 키, URL, 이미지 바이너리와 크기는 승인 전후의 자산 관리 경로에서 별도로 다룬다.
 */
public record SnapshotAssetReference(
        String assetKey,
        String altText
) {
}
