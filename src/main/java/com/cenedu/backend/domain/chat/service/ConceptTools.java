package com.cenedu.backend.domain.chat.service;

import java.util.List;

import com.cenedu.backend.domain.chat.dto.response.ConceptCandidate;
import com.cenedu.backend.domain.chat.dto.response.ConceptView;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * {@link ConceptQueryService} 를 LLM 이 부를 수 있는 도구로 등록하는 껍데기.
 *
 * <p><b>조회 로직을 여기서 바꾸지 않는다.</b> 전부 위임이다. 고정 2단계 파이프라인과 도구 루프가
 * <b>같은 조회 코드</b>를 봐야 두 구조의 성적 차이를 조회 품질 탓으로 돌리지 않을 수 있다.
 * 여기에 필터 하나만 더 붙여도 그 비교가 깨진다.
 *
 * <p>껍데기를 따로 둔 이유는 <b>인자의 출처가 다르기 때문</b>이다. 고정 파이프라인의 인자는 우리
 * 코드가 만들지만, 도구의 인자는 LLM 이 만든다. 신뢰 수준이 다른 입력이 같은 문을 통과하면
 * 안 되므로, LLM 입력에만 필요한 방어(이스케이프·상한)를 이 층에 둔다.
 *
 * <p>{@code ai/client} 도 {@code com.openai} 도 참조하지 않는다. Spring AI 의 어노테이션만 쓰므로
 * {@code AiClientAccessTest} 의 두 규칙에 걸리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ConceptTools {

    private static final Logger log = LoggerFactory.getLogger(ConceptTools.class);

    /** 검색 후보 상한. 고정 파이프라인의 {@code DEFAULT_SEARCH_LIMIT} 과 같은 값이다. */
    private static final int SEARCH_LIMIT_DEFAULT = 5;
    private static final int SEARCH_LIMIT_MAX = 10;

    /**
     * 선수 확장 깊이 상한.
     *
     * <p>3 으로 막는 이유는 비용이 아니라 <b>측정의 한계</b>다. depth 1 은 task_08~14 에서 39턴
     * 회귀 기준선이 있지만 2 이상은 대조군이 없다. 열어 두되 근거 없는 구간까지 열지는 않는다.
     */
    private static final int PREREQ_DEPTH_MAX = 3;

    private final ConceptQueryService conceptQueryService;

    /**
     * 개념 이름으로 후보를 찾는다. 본문은 주지 않는다.
     *
     * <p><b>{@code %} 와 {@code _} 를 이스케이프한다.</b> 이 인자는 LLM 이 만들기 때문이다.
     * 키워드 {@code %} 하나가 그대로 {@code LIKE} 패턴에 들어가면 455행 전부가 후보로 잡혀
     * 컨텍스트에 실린다. 실측상 개념명에 {@code %}·{@code _} 를 가진 것은 0건이라, 이 이스케이프가
     * 정상 검색 결과를 바꾸는 일은 없다 — 순수한 방어다.
     *
     * <p><b>백슬래시는 일부러 손대지 않는다.</b> {@code LIKE} 의 기본 이스케이프 문자가
     * 백슬래시라 이름에 백슬래시가 든 개념 21건이 자기 이름으로 검색되지 않는다(task_14 §4-1).
     * 그건 실재하는 결함이지만, 여기서 고치면 검색 결과 집합이 기준선과 달라져 이번 대조가
     * 성립하지 않는다. 고치는 것은 별도 작업이다.
     */
    @Tool(name = "search_concept",
            description = """
                    개념 이름으로 후보를 찾는다. 학생이 물은 것이 어느 개념인지 특정할 때 쓴다.
                    대부분의 대화는 이 도구로 시작한다.
                    이름과 id 만 돌려주고 설명은 주지 않는다. 설명이 필요하면 get_prereqs 를 쓴다.
                    결과가 비었거나 맞는 개념이 없으면, 학생이 쓴 말 대신 교과서 용어로 바꿔
                    다시 부른다. 예: "마이너스" 대신 "음수", "제곱" 대신 "거듭제곱".
                    개념 id 를 이미 알고 있으면 부르지 않는다.""")
    public List<ConceptCandidate> searchConcept(
            @ToolParam(description = "찾을 개념 이름. 학생이 쓴 말 그대로 또는 교과서 용어 하나")
            String keyword,
            @ToolParam(required = false, description = "받을 후보 수. 기본 5, 최대 10")
            Integer limit) {

        if (keyword == null || keyword.isBlank()) {
            log.info("[도구] search_concept keyword=(빈값) -> 0건");
            return List.of();
        }

        int resolvedLimit = limit == null
                ? SEARCH_LIMIT_DEFAULT
                : Math.clamp(limit, 1, SEARCH_LIMIT_MAX);
        String escaped = escapeLikeWildcards(keyword);

        List<ConceptCandidate> found = conceptQueryService.searchConcepts(List.of(escaped), resolvedLimit);
        log.info("[도구] search_concept keyword={} limit={} -> {}건", keyword, resolvedLimit, found.size());
        return found;
    }

    /**
     * 앵커 개념과 그 선수 개념을 <b>본문까지</b> 돌려준다. 설명 근거는 여기서만 나온다.
     *
     * <p>요청된 depth 와 실제 적용된 depth 를 함께 로그로 남긴다. depth 2 이상 구간은 회귀
     * 기준선이 없어서, LLM 이 그 구간을 얼마나 쓰는지가 그 자체로 측정 대상이다.
     */
    @Tool(name = "get_prereqs",
            description = """
                    개념 id 로 그 개념과 선수 개념들의 설명을 가져온다.
                    설명을 쓸 수 있는 근거는 이 도구의 결과뿐이다.
                    돌려주는 목록의 hop 0 이 물어본 개념 자신이고, hop 이 클수록 더 앞서 배우는 개념이다.
                    depth 는 기본 1 이다. 학생이 "왜 그런지" 나 "더 기초부터" 를 물을 때만 올린다.
                    개념 id 를 모르면 search_concept 를 먼저 부른다.""")
    public List<ConceptView> getPrereqs(
            @ToolParam(description = "설명을 가져올 개념 id. search_concept 결과의 id")
            Long conceptId,
            @ToolParam(required = false, description = "선수 방향 확장 깊이. 기본 1, 최대 3")
            Integer depth) {

        if (conceptId == null) {
            log.info("[도구] get_prereqs conceptId=(없음) -> 0건");
            return List.of();
        }

        int requested = depth == null ? ConceptQueryService.DEFAULT_DEPTH : depth;
        int resolvedDepth = Math.clamp(requested, 1, PREREQ_DEPTH_MAX);

        List<ConceptView> concepts = conceptQueryService.expandPrereqs(
                conceptId, resolvedDepth, ConceptQueryService.DEFAULT_ELEM_HOP_MAX);
        log.info("[도구] get_prereqs conceptId={} depth요청={} depth적용={} -> {}건",
                conceptId, requested, resolvedDepth, concepts.size());
        return concepts;
    }

    /**
     * 소단원에 속한 개념 <b>이름만</b> 돌려준다.
     *
     * <p>본문이 없는 것이 계약이다. 이름을 보고 뜻을 쓰면 그게 곧 지어냄이 된다 — task_08 에서
     * 실제로 일어났고, 그래서 고정 파이프라인의 프롬프트도 이 목록을 안내 용도로만 쓰게 막는다.
     */
    @Tool(name = "get_subunit_concepts",
            description = """
                    학생이 지금 보고 있는 소단원의 개념 이름 목록을 가져온다.
                    이 도구는 이름만 주고 설명을 주지 않는다.
                    그래서 이 결과로는 개념을 설명할 수 없다. 설명하면 지어내는 것이 된다.
                    "무엇을 물어보면 되는지" 를 학생에게 안내할 때만 쓴다.""")
    public List<String> getSubunitConcepts(
            @ToolParam(description = "현재 소단원 id")
            Long subUnitId) {

        List<String> names = conceptQueryService.findSubUnitConceptNames(subUnitId);
        log.info("[도구] get_subunit_concepts subUnitId={} -> {}건", subUnitId, names.size());
        return names;
    }

    /** {@code LIKE} 패턴에서 특수한 뜻을 갖는 두 글자를 리터럴로 만든다. 기본 이스케이프 문자는 백슬래시다. */
    private static String escapeLikeWildcards(String keyword) {
        return keyword.replace("%", "\\%").replace("_", "\\_");
    }
}
