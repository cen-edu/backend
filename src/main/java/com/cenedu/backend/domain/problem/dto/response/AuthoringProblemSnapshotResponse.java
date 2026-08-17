package com.cenedu.backend.domain.problem.dto.response;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;

/** Entity·JSON 문자열 대신 검증 통과 S1 스냅샷을 공개하는 조회 결과다. */
public record AuthoringProblemSnapshotResponse(
        Long sessionId,
        Long versionId,
        Long finalizedQuestionId,
        QuestionSnapshotV1 snapshot
) {
}
