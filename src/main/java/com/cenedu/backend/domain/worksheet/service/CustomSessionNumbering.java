package com.cenedu.backend.domain.worksheet.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 맞춤 학습 차수 파생 규칙. 학습 현황(worksheet)과 평가 결과(grading)가 같은 학습지를 항상 같은
 * 차수로 보여줘야 해서 규칙을 한 곳에 둔다 — 화면마다 복사하면 같은 학습지가 두 화면에서 다른
 * 차수가 된다.
 *
 * <p>차수는 저장 컬럼이 없다. {@code worksheet.parent_worksheet_id} 체인의 <b>깊이</b>로 파생하며,
 * 원본 학습지가 깊이 0이고 그 자식이 1차다. 배정일은 쓰지 않는다 — 학생마다 배정 시점이 달라서
 * 배정일로 매기면 늦게 받은 학생의 첫 맞춤이 3차로 보이고, 같은 라운드인지도 보장되지 않는다.
 * 깊이로 매기면 <b>차수가 학생을 가로지르는 공통 축</b>이 된다.
 */
public final class CustomSessionNumbering {

    /**
     * 깊이 상한. {@code ck_worksheet_parent_precedes}가 순환을 이미 막지만, 제약이 사라져도
     * 무한 루프가 나지 않도록 둔다. 실제 차수는 한 자릿수를 넘지 않는다.
     */
    private static final int MAX_DEPTH = 32;

    private CustomSessionNumbering() {
    }

    /**
     * 차수를 매길 맞춤 학습지 하나.
     *
     * @param parentWorksheetId 직전 차수의 학습지. 원본 학습지를 가리키면 1차다
     */
    public record Node(long worksheetId, Long parentWorksheetId) {
    }

    /**
     * 학습지 ID → 차수(1부터). 원본 학습지는 깊이 0이라 결과에 없다.
     *
     * <p>루트에서 부모-자식 방향으로 훑는다. <b>부모를 못 찾는 행은 결과에서 빠진다</b> — 조용히
     * 1차로 만들면 화면이 거짓말을 한다. 호출부는 결과에 없는 학습지를 차수 미상으로 다뤄야 한다.
     */
    public static Map<Long, Integer> depthByWorksheetId(long rootWorksheetId, Collection<Node> nodes) {
        Map<Long, List<Long>> childrenByParentId = new HashMap<>();
        for (Node node : nodes) {
            if (node.parentWorksheetId() == null) {
                continue;
            }
            childrenByParentId
                    .computeIfAbsent(node.parentWorksheetId(), key -> new ArrayList<>())
                    .add(node.worksheetId());
        }

        Map<Long, Integer> depthByWorksheetId = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>(
                childrenByParentId.getOrDefault(rootWorksheetId, List.of()));
        queue.forEach(worksheetId -> depthByWorksheetId.put(worksheetId, 1));

        while (!queue.isEmpty()) {
            Long parentId = queue.poll();
            int childDepth = depthByWorksheetId.get(parentId) + 1;
            if (childDepth > MAX_DEPTH) {
                continue;
            }
            for (Long childId : childrenByParentId.getOrDefault(parentId, List.of())) {
                // 이미 매긴 학습지는 건너뛴다. 부모가 둘일 수 없으니 정상 데이터에서는 안 걸린다.
                if (depthByWorksheetId.putIfAbsent(childId, childDepth) == null) {
                    queue.add(childId);
                }
            }
        }
        return depthByWorksheetId;
    }
}
