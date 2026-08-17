package com.cenedu.backend.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;


import com.cenedu.backend.domain.chat.dto.response.ConceptLanding;
import com.cenedu.backend.domain.chat.dto.response.ConceptView;
import com.cenedu.backend.domain.chat.entity.enums.GradeBand;
import com.cenedu.backend.support.PostgresTestcontainer;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 하향 탐색 조회 2종의 통합 테스트. 시드(455행 / 1,098엣지)가 들어 있는 DB 를 쓴다.
 *
 * <p><b>개념을 {@code chat_concept.id} 로 잡지 않는다.</b> id 는 BIGSERIAL 이라 적재 순서에
 * 좌우되고, 빈 테스트컨테이너와 로컬 DB 에서 값이 달라질 수 있다. 시드가 UNIQUE 로 보장하는
 * {@code source_concept_id} 를 축으로 쓴다 — {@code curriculum_unit} 을 {@code external_key} 로
 * 잡는 것과 같은 이유다.
 *
 * <p>기대값은 210개 전수 대조(task_23 §2)에서 확인한 것이며, 여기서 값이 달라지면 서비스가 아니라
 * 시드나 쿼리가 바뀐 것이다.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
@DisplayName("하향 탐색 조회")
class DownwardTraversalTest {

    // 1칸 내려가기 — 앵커와 그 1순위
    private static final int SRC_두평면의수직 = 8899;
    private static final int SRC_이항 = 1262;
    private static final int SRC_등식과좌변 = 1260;

    // 선수 0개 — 갈 곳 없음
    private static final int SRC_교점 = 8871;

    // 건너뛰기 — 홉 1 / 홉 7
    private static final int SRC_각 = 8879;
    private static final int SRC_각의크기 = 2053;
    private static final int SRC_정비례반비례활용 = 1273;
    private static final int SRC_대응관계식 = 3703;

    // 초등으로 가는 경로가 아예 없는 개념 (선수는 있다)
    private static final int SRC_교각 = 8884;

    @Autowired
    private ConceptQueryService conceptQueryService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("1칸: 앵커의 1순위 선수를 하나 돌려준다")
    void findsNextStepDown() {
        assertThat(nextStepName(SRC_두평면의수직)).isEqualTo("공간에서 직선과 평면의 위치 관계");
        assertThat(nextStepName(SRC_이항)).isEqualTo("등식과 좌변, 우변, 양변");
        assertThat(nextStepName(SRC_등식과좌변)).isEqualTo("문자를 사용한 식 나타내기");
    }

    @Test
    @DisplayName("1칸: 선수가 0개면 빈 결과다 — 예외가 아니라 정상 경로")
    void nextStepIsEmptyWhenNoPrereq() {
        assertThat(conceptQueryService.findNextStepDown(conceptId(SRC_교점))).isEmpty();
    }

    @Test
    @DisplayName("1칸: 없는 개념이나 null 도 빈 결과다")
    void nextStepIsEmptyForUnknownConcept() {
        assertThat(conceptQueryService.findNextStepDown(null)).isEmpty();
        assertThat(conceptQueryService.findNextStepDown(-1L)).isEmpty();
    }

    @Test
    @DisplayName("착지: 가장 가까운 초등 개념과 홉을 돌려준다")
    void findsNearestElementary() {
        // 홉 1 — 바로 아래가 초등이다.
        ConceptLanding near = landing(SRC_각);
        assertThat(near.hop()).isEqualTo(1);
        assertThat(near.concept().name()).isEqualTo("각의 크기");
        assertThat(near.concept().gradeBand()).isEqualTo(GradeBand.ELEMENTARY);
        assertThat(near.concept().id()).isEqualTo(conceptId(SRC_각의크기));

        // 홉 7 — 이 데이터에서 가장 먼 착지다.
        ConceptLanding far = landing(SRC_정비례반비례활용);
        assertThat(far.hop()).isEqualTo(7);
        assertThat(far.concept().id()).isEqualTo(conceptId(SRC_대응관계식));
    }

    @Test
    @DisplayName("착지: 초등으로 가는 경로가 없으면 빈 결과다")
    void landingIsEmptyWhenNoRoute() {
        // 교각은 선수가 있지만 그 줄기가 초등에 닿지 않는다. 210개 중 10개가 여기 해당한다.
        assertThat(conceptQueryService.findNextStepDown(conceptId(SRC_교각))).isPresent();
        assertThat(conceptQueryService.findNearestElementary(conceptId(SRC_교각))).isEmpty();

        // 선수 자체가 없는 경우도 빈 결과다.
        assertThat(conceptQueryService.findNearestElementary(conceptId(SRC_교점))).isEmpty();
    }

    @Test
    @DisplayName("착지: maxDepth 를 낮추면 못 닿는다 — 상한이 실제로 작동한다")
    void maxDepthActuallyLimitsSearch() {
        Long anchor = conceptId(SRC_정비례반비례활용);   // 착지까지 7홉

        assertThat(conceptQueryService.findNearestElementary(anchor, 7)).isPresent();
        assertThat(conceptQueryService.findNearestElementary(anchor, 6)).isEmpty();
        assertThat(conceptQueryService.findNearestElementary(anchor, 1)).isEmpty();
    }

    @Test
    @DisplayName("착지: maxDepth 는 거부하지 않고 범위 안으로 누른다")
    void maxDepthIsClamped() {
        Long anchor = conceptId(SRC_정비례반비례활용);

        // 0 이하는 1 로 눌린다 — 7홉짜리라 못 닿는다.
        assertThat(conceptQueryService.findNearestElementary(anchor, 0)).isEmpty();
        assertThat(conceptQueryService.findNearestElementary(anchor, -5)).isEmpty();

        // 상한(10)을 넘겨도 예외 없이 상한으로 눌려 동작한다.
        assertThat(conceptQueryService.findNearestElementary(anchor, 999))
                .isPresent()
                .get()
                .extracting(ConceptLanding::hop)
                .isEqualTo(7);
    }

    @Test
    @DisplayName("동점이면 id 오름차순으로 하나를 고른다")
    void tieIsBrokenByIdAscending() {
        // `각` 의 선수 둘은 학년값이 같다(초4-1). 둘 중 id 가 작은 쪽이 1순위여야 한다.
        Long anchor = conceptId(SRC_각);
        Long expected = conceptId(SRC_각의크기);

        ConceptView picked = conceptQueryService.findNextStepDown(anchor).orElseThrow();

        assertThat(picked.id()).isEqualTo(expected);
        assertThat(minPrereqId(anchor)).isEqualTo(expected);
    }

    private String nextStepName(int sourceConceptId) {
        return conceptQueryService.findNextStepDown(conceptId(sourceConceptId))
                .map(ConceptView::name)
                .orElseThrow();
    }

    private ConceptLanding landing(int sourceConceptId) {
        return conceptQueryService.findNearestElementary(conceptId(sourceConceptId)).orElseThrow();
    }

    /** 시드가 UNIQUE 로 보장하는 축. id 는 적재 순서에 좌우되므로 쓰지 않는다. */
    private Long conceptId(int sourceConceptId) {
        return entityManager.createQuery(
                        "SELECT c.id FROM ChatConcept c WHERE c.sourceConceptId = :src", Long.class)
                .setParameter("src", sourceConceptId)
                .getSingleResult();
    }

    /** 동점 검증용. 그 앵커의 선수 중 가장 작은 id. */
    private Long minPrereqId(Long conceptId) {
        return entityManager.createQuery("""
                        SELECT MIN(p.prereqConceptId) FROM ChatConceptPrereq p
                        WHERE p.conceptId = :conceptId
                        """, Long.class)
                .setParameter("conceptId", conceptId)
                .getSingleResult();
    }
}
