package com.cenedu.backend.domain.chat.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.chat.dto.response.ConceptContext;
import com.cenedu.backend.domain.chat.dto.response.ConceptView;
import com.cenedu.backend.domain.chat.entity.ChatConcept;
import com.cenedu.backend.domain.chat.repository.ChatConceptRepository;
import com.cenedu.backend.domain.chat.repository.ChatConceptRepository.ConceptHop;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개념 챗봇의 도구 계층. 순수 DB 조회만 하고 LLM 을 부르지 않는다.
 *
 * <p>도구를 따로 떼어 둔 이유는 실패 지점을 분리하기 위해서다. 검색이 앵커를 못 찾은 것과
 * LLM 이 근거를 무시한 것은 고칠 곳이 다른데, 한 덩어리로 두면 구분이 안 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConceptQueryService {

    /**
     * 선수 확장의 기본 깊이.
     *
     * <p>1 인 이유는 포화 시점이 앵커마다 다르기 때문이다. 맞꼭지각은 depth 1 에서 4개로
     * 포화해 5로 올려도 결과가 같지만, 곱셈 기호의 생략은 hop 7 까지 28개로 번진다.
     * 하나의 값으로 맞출 수 없어 기본값만 두고 파라미터로 열어 둔다.
     */
    public static final int DEFAULT_DEPTH = 1;

    /**
     * 주입할 초등 개념의 elem_hop 상한.
     *
     * <p>elem_hop 은 학년이 아니라 중1 개념에서의 거리다. 그래서 1 로 두면 같은 초2 개념이라도
     * {@code 곱셈식}(elem_hop 1)은 들어오고 {@code 곱셈식으로 나타내기}(elem_hop 2)는 빠진다.
     * 더 파고들면 초1 덩어리가 붙어 컨텍스트가 희석된다.
     */
    public static final short DEFAULT_ELEM_HOP_MAX = 1;

    /** 앵커 후보로 훑을 검색 결과 상한. 첫 건만 앵커로 쓰지만 상위가 후보를 볼 수 있게 남긴다. */
    private static final int DEFAULT_SEARCH_LIMIT = 5;

    private final ChatConceptRepository chatConceptRepository;

    /**
     * 키워드를 순서대로 두드려 결과가 나오는 첫 키워드에서 멈춘다.
     *
     * <p>복수로 받는 이유는 {@code ILIKE} 부분 일치의 한계다 — "제곱"으로는 {@code 지수} 를
     * 못 찾으므로 상위가 {@code ["제곱","거듭제곱","지수"]} 처럼 넘기고 여기서 순차 재시도한다.
     */
    public List<ChatConcept> searchConcepts(List<String> keywords, int limitPerKeyword) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            List<ChatConcept> found = chatConceptRepository
                    .findByNameContainingIgnoreCase(keyword, Limit.of(limitPerKeyword));
            if (!found.isEmpty()) {
                return found;
            }
        }
        return List.of();
    }

    /** 앵커에서 선수 방향으로 확장한 개념을 hop 오름차순으로 돌려준다. hop 0 은 앵커 자신이다. */
    public List<ConceptView> expandPrereqs(Long conceptId, int depth, short elemHopMax) {
        List<ConceptHop> closure = chatConceptRepository
                .findPrereqClosure(conceptId, depth, elemHopMax);
        if (closure.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> hopById = new LinkedHashMap<>();
        closure.forEach(row -> hopById.put(row.getId(), row.getHop()));

        return chatConceptRepository.findAllById(hopById.keySet()).stream()
                .map(concept -> ConceptView.from(concept, hopById.get(concept.getId())))
                .sorted(Comparator.comparingInt(ConceptView::hop)
                        .thenComparing(ConceptView::id))
                .toList();
    }

    /** 기본 깊이와 기본 초등 상한으로 확장한다. */
    public List<ConceptView> expandPrereqs(Long conceptId) {
        return expandPrereqs(conceptId, DEFAULT_DEPTH, DEFAULT_ELEM_HOP_MAX);
    }

    /** 한 소단원에 속한 개념의 이름 목록을 돌려준다. 본문은 싣지 않는다. */
    public List<String> findSubUnitConceptNames(Long subUnitId) {
        if (subUnitId == null) {
            return List.of();
        }
        return chatConceptRepository.findBySubUnitIdOrderByIdAsc(subUnitId).stream()
                .map(ChatConcept::getName)
                .toList();
    }

    /**
     * 검색·확장·소단원 목록을 묶어 한 번의 조회 근거를 만든다.
     *
     * <p>앵커는 검색 결과의 첫 건으로 고정한다. 앵커 선택을 LLM 에 맡기는 것은 다음 단계이며,
     * 지금은 고정 규칙으로 두어야 검색 품질만 따로 측정할 수 있다.
     *
     * <p>앵커도 없고 소단원 개념도 0건이면 {@code empty = true} 로 표시한다. 근거가 전무하니
     * LLM 을 부르지 말라는 신호이며, 호출 여부는 상위가 정한다. 실제로 발생하는 경로다 —
     * 소단원 {@code 그래프를 나타내고 해석하기} 는 개념이 0건이라, 그 소단원에서 검색까지
     * 실패하면 줄 수 있는 근거가 하나도 없다.
     */
    public ConceptContext buildContext(Long subUnitId, List<String> keywords,
                                       int depth, short elemHopMax) {
        List<String> subUnitConceptNames = findSubUnitConceptNames(subUnitId);
        List<ChatConcept> searched = searchConcepts(keywords, DEFAULT_SEARCH_LIMIT);

        if (searched.isEmpty()) {
            return subUnitConceptNames.isEmpty()
                    ? ConceptContext.noEvidence()
                    : ConceptContext.of(null, List.of(), subUnitConceptNames);
        }

        ChatConcept anchor = searched.get(0);
        List<ConceptView> concepts = expandPrereqs(anchor.getId(), depth, elemHopMax);
        return ConceptContext.of(ConceptView.from(anchor, 0), concepts, subUnitConceptNames);
    }

    /** 기본 깊이와 기본 초등 상한으로 근거를 만든다. */
    public ConceptContext buildContext(Long subUnitId, List<String> keywords) {
        return buildContext(subUnitId, keywords, DEFAULT_DEPTH, DEFAULT_ELEM_HOP_MAX);
    }
}
