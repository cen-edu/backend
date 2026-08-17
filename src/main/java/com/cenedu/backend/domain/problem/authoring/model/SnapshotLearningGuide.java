package com.cenedu.backend.domain.problem.authoring.model;

import java.util.List;

/**
 * 학생 풀이 화면 우측에 표시할 최소 개념 안내다.
 *
 * <p>공식, 계산 절차, 답을 유추할 수 있는 직접적인 힌트를 포함하지 않는다. {@code keyPoints}는
 * 1개 이상 3개 이하이며, KeyPoint 에 직접적인 정답 힌트가 작성되지 않도록
 * 첫 항목에는 가장 핵심적인 개념 정보를 두고, 이 후 항목은 해당 항목에서 파생된 정보를 담도록 한다.
 */
public record SnapshotLearningGuide(
        String conceptTitle,
        String summary,
        List<String> keyPoints
) {
}
