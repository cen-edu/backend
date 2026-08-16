package com.cenedu.backend.domain.problem.ai.model;

import java.util.List;

/**
 * 문제 한 문항의 V1 스냅샷이다.
 *
 * <p>생성·수정 에이전트가 만들고 정규화·검증 경로가 소비한다. 모든 목록은 호출자가
 * {@code null} 대신 빈 목록으로 제공해야 하며, 문제 유형별 허용 조합은 별도 Validator가 검사한다.
 * 승인자, 검증 상태, 저장소 키와 같은 처리 이력은 수정 세션·버전에서 관리한다.
 */
public record QuestionSnapshotV1(
        int schemaVersion,
        SnapshotMetadata metadata,
        List<SnapshotContentBlock> contentBlocks,
        List<SnapshotAssetReference> assets,
        List<SnapshotChoice> choices,
        List<SnapshotStep> steps,
        List<SnapshotAnswerUnit> answerUnits,
        String explanation,
        SnapshotLearningGuide learningGuide,
        List<SnapshotRubricItem> rubricItems
) {

    /** 현재 문항 스냅샷 계약의 버전이다. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
