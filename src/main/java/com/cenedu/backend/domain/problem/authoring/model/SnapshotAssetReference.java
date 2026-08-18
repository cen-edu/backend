package com.cenedu.backend.domain.problem.authoring.model;

/**
 * FIGURE 블록이 참조하는 이미지의 논리 정보다.
 *
 * <p>{@code altText}는 그림에 실제로 보이는 정보만 설명하며 정답, 계산 결과, 풀이 추론을 포함하지
 * 않는다. 그림 생성 시 참조할 정보 역시 이를 참조한다.
 * 저장소 키, URL, 이미지 바이너리와 크기는 승인 전후의 자산 관리 경로에서 별도로 다룬다.
 */
public record SnapshotAssetReference(
        String assetKey,
        String altText
) {
}
