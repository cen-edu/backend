package com.cenedu.backend.domain.worksheet.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.worksheet.service.CustomSessionNumbering.Node;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("맞춤 차수 파생")
class CustomSessionNumberingTest {

    private static final long ROOT = 100L;

    @Test
    @DisplayName("원본 학습지는 깊이 0이라 결과에 없다")
    void rootIsNotNumbered() {
        Map<Long, Integer> depths = CustomSessionNumbering.depthByWorksheetId(
                ROOT, List.of(new Node(101L, ROOT)));

        assertThat(depths).doesNotContainKey(ROOT);
        assertThat(depths).containsEntry(101L, 1);
    }

    @Test
    @DisplayName("같은 부모를 가진 학습지는 배정 순서와 무관하게 같은 차수다")
    void siblingsShareDepth() {
        // 학생 A 1차·2차, 학생 B 1차. B가 가장 늦게 배정돼도 1차여야 한다.
        Map<Long, Integer> depths = CustomSessionNumbering.depthByWorksheetId(ROOT, List.of(
                new Node(101L, ROOT),    // A 1차
                new Node(103L, 101L),    // A 2차
                new Node(102L, ROOT)));  // B 1차

        assertThat(depths).containsExactlyInAnyOrderEntriesOf(
                Map.of(101L, 1, 102L, 1, 103L, 2));
    }

    @Test
    @DisplayName("부모를 못 찾는 학습지는 결과에서 빠진다 — 조용히 1차로 만들지 않는다")
    void orphanIsExcluded() {
        Map<Long, Integer> depths = CustomSessionNumbering.depthByWorksheetId(ROOT, List.of(
                new Node(101L, ROOT),
                new Node(999L, 900L)));  // 이 묶음에 없는 부모

        assertThat(depths).containsOnlyKeys(101L);
    }

    @Test
    @DisplayName("parentWorksheetId 가 null 인 행은 결과에서 빠진다")
    void nullParentIsExcluded() {
        Map<Long, Integer> depths = CustomSessionNumbering.depthByWorksheetId(ROOT, List.of(
                new Node(101L, ROOT),
                new Node(102L, null)));

        assertThat(depths).containsOnlyKeys(101L);
    }

    @Test
    @DisplayName("입력 순서를 섞어도 결과가 같다")
    void resultIsOrderIndependent() {
        List<Node> nodes = new ArrayList<>(List.of(
                new Node(101L, ROOT), new Node(102L, 101L), new Node(103L, 102L),
                new Node(104L, ROOT), new Node(105L, 104L)));
        Map<Long, Integer> expected = CustomSessionNumbering.depthByWorksheetId(ROOT, nodes);

        for (int i = 0; i < 20; i++) {
            Collections.shuffle(nodes);
            assertThat(CustomSessionNumbering.depthByWorksheetId(ROOT, nodes)).isEqualTo(expected);
        }
        assertThat(expected).containsExactlyInAnyOrderEntriesOf(
                Map.of(101L, 1, 102L, 2, 103L, 3, 104L, 1, 105L, 2));
    }

    @Test
    @DisplayName("순환이 들어와도 무한 루프에 빠지지 않는다")
    void cycleTerminates() {
        // DB CHECK 가 막지만 제약이 사라진 경우를 대비한다.
        Map<Long, Integer> depths = CustomSessionNumbering.depthByWorksheetId(ROOT, List.of(
                new Node(101L, ROOT),
                new Node(102L, 101L),
                new Node(101L, 102L)));  // 101 -> 102 -> 101

        assertThat(depths).containsEntry(101L, 1).containsEntry(102L, 2);
    }

    @Test
    @DisplayName("깊은 체인도 깊이 상한 안에서 순서대로 매긴다")
    void deepChain() {
        List<Node> nodes = new ArrayList<>();
        long parent = ROOT;
        for (long id = 201L; id <= 210L; id++) {
            nodes.add(new Node(id, parent));
            parent = id;
        }

        Map<Long, Integer> depths = CustomSessionNumbering.depthByWorksheetId(ROOT, nodes);

        assertThat(depths).hasSize(10);
        assertThat(depths).containsEntry(201L, 1).containsEntry(210L, 10);
    }

    @Test
    @DisplayName("맞춤이 없으면 빈 맵이다")
    void emptyInput() {
        assertThat(CustomSessionNumbering.depthByWorksheetId(ROOT, List.of())).isEmpty();
    }
}
