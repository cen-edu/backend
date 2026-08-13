package com.cenedu.backend.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cenedu.backend.domain.chat.dto.response.ConceptContext;
import com.cenedu.backend.domain.chat.dto.response.ConceptView;
import com.cenedu.backend.domain.chat.entity.ChatConcept;
import com.cenedu.backend.domain.chat.repository.ChatConceptPrereqRepository;
import com.cenedu.backend.domain.chat.repository.ChatConceptRepository;
import com.cenedu.backend.support.PostgresTestcontainer;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 개념 조회 도구의 통합 테스트. 시드(455행 / 1,098엣지)가 들어 있는 DB 를 쓴다.
 *
 * <p>로컬 DB 가 아니라 테스트컨테이너에 붙는다. 시드가 Flyway 마이그레이션이라 빈 컨테이너에서도
 * 그대로 적재되므로, 로컬에 남아 있는 스키마와 무관하게 같은 값이 나온다. CI 에서도 돈다.
 *
 * <p>기대값은 task_03·04 에서 psql 로 측정한 값을 그대로 옮긴 것이다. 여기서 값이 달라지면
 * 서비스가 아니라 시드나 쿼리가 바뀐 것이다.
 *
 * <p>전부 읽기 전용이라 시드를 변형하지 않는다. 쓰기가 없으므로 롤백도 필요 없다.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
@DisplayName("개념 조회 도구")
class ConceptQueryServiceTest {

    /**
     * 소단원은 대리키가 아니라 {@code external_key} 로 잡는다. {@code curriculum_unit.id} 는
     * 적재 순서에 따라 달라지기 때문이다 — 옛 psql 로더는 소단원을 먼저 넣어 1~18 이 되지만,
     * {@code V20260812_1628} 은 대단원 4 → 중단원 8 → 소단원 18 순이라 빈 컨테이너에서는
     * 소단원이 13~30 이 된다. 시드 SQL 이 이름·id 가 아니라 external_key 로만 조인하는 것과
     * 같은 이유다.
     */
    private static final String KEY_BASIC_FIGURE = "EBS-M1-MATH-221311";

    /** 소단원 '그래프를 나타내고 해석하기'. 개념이 0건인 유일한 소단원이다. */
    private static final String KEY_GRAPH_READING = "EBS-M1-MATH-221422";

    private static final long ANCHOR_VERTICAL_ANGLE = 16L;      // 맞꼭지각
    private static final long ANCHOR_OMIT_MULTIPLY = 53L;       // 곱셈 기호의 생략
    private static final long ANCHOR_COMPLEX_EQUATION = 87L;    // 복잡한 일차방정식의 풀이

    private static final short ELEM_HOP_MAX = 1;

    @Autowired
    private ConceptQueryService conceptQueryService;

    @Autowired
    private ChatConceptRepository chatConceptRepository;

    @Autowired
    private ChatConceptPrereqRepository chatConceptPrereqRepository;

    @Autowired
    private EntityManager entityManager;

    private long subUnitId(String externalKey) {
        return entityManager.createQuery(
                        "SELECT u.id FROM CurriculumUnit u WHERE u.externalKey = :key", Long.class)
                .setParameter("key", externalKey)
                .getSingleResult();
    }

    @Test
    @DisplayName("1. 시드가 455행 / 1,098엣지 그대로 들어 있다")
    void seedRowCounts() {
        assertThat(chatConceptRepository.count()).isEqualTo(455);
        assertThat(chatConceptPrereqRepository.count()).isEqualTo(1098);
    }

    @Test
    @DisplayName("2. 자기 자신을 선수로 갖는 엣지가 없다")
    void noSelfReferencingEdge() {
        long selfEdges = chatConceptPrereqRepository.findAll().stream()
                .filter(edge -> edge.getConceptId().equals(edge.getPrereqConceptId()))
                .count();

        assertThat(selfEdges).isZero();
    }

    @Test
    @DisplayName("3. 맞꼭지각 depth 1 확장은 4건이고 원문 합계가 301자다")
    void expandVerticalAngleDepthOne() {
        List<ConceptView> concepts =
                conceptQueryService.expandPrereqs(ANCHOR_VERTICAL_ANGLE, 1, ELEM_HOP_MAX);

        assertThat(concepts).hasSize(4);
        assertThat(concepts.get(0).hop()).isZero();
        assertThat(concepts.get(0).name()).isEqualTo("맞꼭지각");

        int rawLength = chatConceptRepository.findAllById(
                        concepts.stream().map(ConceptView::id).toList()).stream()
                .mapToInt(concept -> concept.getDescription().length())
                .sum();
        int viewLength = concepts.stream()
                .mapToInt(concept -> concept.description().length())
                .sum();

        // psql 로 측정한 301 은 DB 원문 기준이다. ConceptView 는 리터럴 \n 두 글자를 실제
        // 개행 한 글자로 바꾸므로 그만큼 짧아진다. 이 차이가 곧 치환된 줄바꿈 개수다.
        assertThat(rawLength).isEqualTo(301);
        assertThat(viewLength).isEqualTo(296);
        assertThat(rawLength - viewLength).isEqualTo(5);
    }

    @Test
    @DisplayName("4. 맞꼭지각은 depth 1 에서 포화해 depth 5 결과가 같다")
    void expandVerticalAngleSaturates() {
        List<ConceptView> depthOne =
                conceptQueryService.expandPrereqs(ANCHOR_VERTICAL_ANGLE, 1, ELEM_HOP_MAX);
        List<ConceptView> depthFive =
                conceptQueryService.expandPrereqs(ANCHOR_VERTICAL_ANGLE, 5, ELEM_HOP_MAX);

        assertThat(depthFive).isEqualTo(depthOne);
    }

    @Test
    @DisplayName("5. 곱셈 기호의 생략 depth 10 은 28건이고 최대 hop 이 5이며 depth 를 올려도 같다")
    void expandOmitMultiplyTerminates() {
        List<ConceptView> concepts =
                conceptQueryService.expandPrereqs(ANCHOR_OMIT_MULTIPLY, 10, ELEM_HOP_MAX);

        assertThat(concepts).hasSize(28);
        assertThat(concepts.stream().map(ConceptView::id).distinct().count()).isEqualTo(28);

        int maxHop = concepts.stream().mapToInt(ConceptView::hop).max().orElseThrow();

        // 두 숫자를 구분해야 한다. 원시 walk 기준 MAX(hop) = 7, 서비스가 노출하는 개념별
        // MIN(hop) 의 최댓값 = 5. 리포지토리가 GROUP BY 로 접어서 돌려주므로 서비스 계약은
        // 후자다. 순환 방어는 이 값이 아니라 depth 포화로 검증한다 — path 조건을 빼도 도달
        // 가능한 개념 집합(28건)과 MIN(hop)(5)은 그대로이고, 순환 폭발은 원시 walk 행 수와
        // 실행 시간에만 나타나기 때문이다. 즉 5 라는 값 자체로는 순환 방어를 감지할 수 없다.
        assertThat(maxHop).isEqualTo(5);

        // depth 를 5배로 올려도 같은 결과가 즉시 나온다. 확장할 노드가 없어 포화했다는 뜻이며,
        // 서비스 계약 안에서 순환이 막혀 있음을 보일 수 있는 유일한 단언이다.
        assertThat(conceptQueryService.expandPrereqs(ANCHOR_OMIT_MULTIPLY, 50, ELEM_HOP_MAX))
                .isEqualTo(concepts);
    }

    @Test
    @DisplayName("6. 복잡한 일차방정식의 풀이 depth 3 확장은 17건이다")
    void expandComplexEquation() {
        List<ConceptView> concepts =
                conceptQueryService.expandPrereqs(ANCHOR_COMPLEX_EQUATION, 3, ELEM_HOP_MAX);

        assertThat(concepts).hasSize(17);
    }

    @Test
    @DisplayName("7. 이름으로 개념을 찾는다")
    void searchByName() {
        List<ChatConcept> found = conceptQueryService.searchConcepts(List.of("맞꼭지각"), 5);

        assertThat(found).isNotEmpty();
        assertThat(found).allMatch(concept -> concept.getName().contains("맞꼭지각"));
    }

    /**
     * 이 테스트는 <b>기대값을 뒤집은 것</b>이다. 예전에는 "첫 키워드가 걸리면 뒤는 시도하지 않는다"
     * 를 단언했는데, 그 규칙이 task_08 에서 답을 망쳐 폐기했다(task_09). 회귀가 아니라 계약 변경이다.
     */
    @Test
    @DisplayName("8. 뒤 키워드도 전부 시도해 결과를 합친다")
    void mergesEveryKeyword() {
        List<ChatConcept> firstOnly = conceptQueryService.searchConcepts(List.of("제곱"), 5);
        List<ChatConcept> merged = conceptQueryService.searchConcepts(List.of("제곱", "지수"), 5);

        assertThat(firstOnly).isNotEmpty()
                .noneMatch(concept -> concept.getName().equals("지수"));
        assertThat(merged).anyMatch(concept -> concept.getName().equals("지수"));
    }

    @Test
    @DisplayName("9. 어떤 키워드도 걸리지 않으면 빈 리스트다")
    void returnsEmptyWhenNoKeywordHits() {
        assertThat(conceptQueryService.searchConcepts(List.of("존재하지않는개념xyz"), 5)).isEmpty();
    }

    @Test
    @DisplayName("13. 이름이 정확히 일치하는 개념이 1위다 — 부분 일치가 앞을 채우지 않는다")
    void exactNameRanksFirst() {
        // 정렬 전에는 '다항식'·'단항식' 이 앞을 채워 '항' 이 상위 5건 밖으로 밀렸다.
        assertThat(conceptQueryService.searchConcepts(List.of("항"), 5).get(0).getName())
                .isEqualTo("항");
        assertThat(conceptQueryService.searchConcepts(List.of("인수"), 5).get(0).getName())
                .isEqualTo("인수");
    }

    /**
     * 뒤쪽 키워드의 완전일치가 앞쪽 키워드의 부분일치를 이긴다.
     *
     * <p>{@code 소인수} 는 {@code 소인수분해} 에 접두일치(등급 2)하고 {@code 지수} 는 완전일치(등급 0)다.
     * 첫 히트에서 멈추던 시절에는 {@code 소인수} 가 결과를 내는 순간 {@code 지수} 를 시도조차 못 했다.
     */
    @Test
    @DisplayName("14. 뒤쪽 키워드의 완전일치가 앞쪽 키워드의 부분일치를 이긴다")
    void exactMatchBeatsEarlierPartialMatch() {
        assertThat(conceptQueryService.searchConcepts(List.of("소인수", "지수"), 5).get(0).getName())
                .isEqualTo("지수");
    }

    /**
     * 등급이 다르면 키워드 순서가 순위를 바꾸지 못한다.
     *
     * <p>같은 등급(완전일치끼리)에서는 순서가 타이브레이크로 쓰인다. 16-2 번이 그 경우다.
     */
    @Test
    @DisplayName("15. 등급이 다르면 키워드 순서를 바꿔도 1위가 같다")
    void rankingIsIndependentOfKeywordOrderAcrossGrades() {
        List<ChatConcept> forward = conceptQueryService.searchConcepts(List.of("소인수", "지수"), 5);
        List<ChatConcept> reversed = conceptQueryService.searchConcepts(List.of("지수", "소인수"), 5);

        assertThat(forward.get(0).getName()).isEqualTo("지수");
        assertThat(reversed.get(0).getName()).isEqualTo("지수");
    }

    /**
     * 접미 일치를 접두 일치보다 위에 둔 규칙을 고정한다.
     *
     * <p>이 두 건이 근거다. 접두를 먼저 두면 {@code 최대공약수의 활용}·{@code 표와 그래프로 나타내기}
     * 가 1위가 되어 task_05 대비 나빠진다(실측).
     */
    @Test
    @DisplayName("16. 접미 일치가 접두 일치보다 앞선다")
    void suffixMatchOutranksPrefixMatch() {
        assertThat(conceptQueryService.searchConcepts(List.of("최대공약수"), 5).get(0).getName())
                .isEqualTo("공약수와 최대공약수");
        assertThat(conceptQueryService.searchConcepts(List.of("표"), 5).get(0).getName())
                .isEqualTo("도수분포표");
    }

    /**
     * 서로 다른 키워드가 각각 다른 개념에 완전일치할 때 <b>앞선 키워드</b>가 이긴다.
     *
     * <p>이게 없으면 짧은 이름이 이겨서 엉뚱한 앵커가 잡힌다. 기준선 측정에서 실제로 걸렸다 —
     * "소인수분해를 왜 하는 거예요?" 가 {@code 약수} 를, "이항이 뭐예요?" 가 {@code 항} 을 앵커로
     * 삼았다. 등급을 넘나드는 순서 독립성(14·15번)은 그대로다.
     */
    @Test
    @DisplayName("16-2. 완전일치가 여럿이면 앞선 키워드에 맞은 개념이 1위다")
    void earlierKeywordWinsAmongExactMatches() {
        assertThat(conceptQueryService.searchConcepts(List.of("소인수분해", "약수"), 5).get(0).getName())
                .isEqualTo("소인수분해");
        assertThat(conceptQueryService.searchConcepts(List.of("이항", "항"), 5).get(0).getName())
                .isEqualTo("이항");
    }

    @Test
    @DisplayName("17. 두 키워드가 같은 개념을 잡아도 결과에 한 번만 담긴다")
    void mergedResultHasNoDuplicate() {
        List<Long> merged = ids(conceptQueryService.searchConcepts(List.of("소인수분해", "인수"), 5));

        assertThat(merged).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("18. 같은 호출을 두 번 해도 순서가 같다")
    void rankingIsReproducible() {
        List<String> keywords = List.of("각", "점");

        assertThat(ids(conceptQueryService.searchConcepts(keywords, 5)))
                .isEqualTo(ids(conceptQueryService.searchConcepts(keywords, 5)));
    }

    @Test
    @DisplayName("19. 빈 키워드 목록이면 빈 리스트다")
    void returnsEmptyForEmptyKeywords() {
        assertThat(conceptQueryService.searchConcepts(List.of(), 5)).isEmpty();
    }

    private static List<Long> ids(List<ChatConcept> concepts) {
        return concepts.stream().map(ChatConcept::getId).toList();
    }

    @Test
    @DisplayName("10. ConceptView 의 본문은 리터럴 \\n 대신 실제 개행을 갖는다")
    void conceptViewSubstitutesLiteralNewline() {
        ConceptView anchor =
                conceptQueryService.expandPrereqs(ANCHOR_VERTICAL_ANGLE, 1, ELEM_HOP_MAX).get(0);

        assertThat(anchor.description()).contains("\n");
        assertThat(anchor.description()).doesNotContain("\\n");

        ChatConcept raw = chatConceptRepository.findById(ANCHOR_VERTICAL_ANGLE).orElseThrow();
        assertThat(raw.getDescription()).contains("\\n");
    }

    @Test
    @DisplayName("11. 개념 0건 소단원에서 검색까지 실패하면 근거가 전무하다")
    void emptyContextWhenNoAnchorAndNoSubUnitConcept() {
        long subUnitId = subUnitId(KEY_GRAPH_READING);

        ConceptContext context =
                conceptQueryService.buildContext(subUnitId, List.of("존재하지않는개념xyz"));

        assertThat(conceptQueryService.findSubUnitConceptNames(subUnitId)).isEmpty();
        assertThat(context.empty()).isTrue();
        assertThat(context.anchor()).isNull();
        assertThat(context.concepts()).isEmpty();
    }

    @Test
    @DisplayName("12. 정상 소단원에서 앵커를 찾으면 근거가 채워진다")
    void contextFilledForNormalSubUnit() {
        ConceptContext context =
                conceptQueryService.buildContext(subUnitId(KEY_BASIC_FIGURE), List.of("맞꼭지각"));

        assertThat(context.empty()).isFalse();
        assertThat(context.anchor()).isNotNull();
        assertThat(context.anchor().name()).isEqualTo("맞꼭지각");
        assertThat(context.anchor().hop()).isZero();
        assertThat(context.concepts()).hasSize(4);
        assertThat(context.subUnitConceptNames()).isNotEmpty();
    }
}
