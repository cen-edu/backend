package com.cenedu.backend.domain.chat.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cenedu.backend.domain.chat.dto.response.ConceptCandidate;
import com.cenedu.backend.domain.chat.dto.response.ConceptContext;
import com.cenedu.backend.domain.chat.dto.response.ConceptLanding;
import com.cenedu.backend.domain.chat.dto.response.ConceptView;
import com.cenedu.backend.domain.chat.entity.ChatConcept;
import com.cenedu.backend.domain.chat.repository.ChatConceptRepository;
import com.cenedu.backend.domain.chat.repository.ChatConceptRepository.ConceptHop;

import lombok.RequiredArgsConstructor;

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

    /**
     * 건너뛰기 착지의 기본 탐색 깊이.
     *
     * <p>7 인 이유는 <b>실측상 회수 가능한 최대 홉이 7</b>이기 때문이다. 중1 210개 중 초등에 닿는
     * 181개가 전부 7홉 안에 있으므로, 이 값이면 도달 가능한 것을 하나도 놓치지 않는다.
     */
    private static final int DEFAULT_LANDING_DEPTH = 7;

    private static final int MIN_LANDING_DEPTH = 1;

    /**
     * 탐색 깊이 상한.
     *
     * <p>재귀 CTE 는 노드가 아니라 <b>경로</b>를 열거해서 깊이를 열면 폭발한다. 중1 210개를 한꺼번에
     * 돌린 실측에서 상한 6 은 29ms 였지만 상한 20 은 180초 안에 끝나지 않았다. 런타임에는 앵커
     * 하나만 돌아 훨씬 싸지만, 그 성질 자체는 남아 있으므로 열어 두지 않는다.
     */
    private static final int MAX_LANDING_DEPTH = 10;

    private final ChatConceptRepository chatConceptRepository;

    /**
     * 키워드를 <b>전부</b> 두드려 결과를 합치고, 중복을 없앤 뒤 다시 순위를 매겨 돌려준다.
     *
     * <p>예전에는 결과가 나오는 첫 키워드에서 멈췄다. 그 규칙이 task_08 에서 실제로 답을 망쳤다 —
     * "지수가 뭐예요?" 에 상위가 {@code ["거듭제곱","지수"]} 를 넘겼는데, {@code 거듭제곱} 이
     * 먼저 걸리는 바람에 정작 완전일치인 {@code 지수} 는 시도조차 되지 않았다.
     *
     * <p><b>키워드 순서를 순위에 반영하지 않는다.</b> 상위 프롬프트가 "구체적인 것부터" 를
     * 지시하지만 모델이 그 순서를 자주 어긴다. 순서를 믿는 대신 완전일치가 이기게 둔다.
     *
     * <p>키워드 수만큼 쿼리가 나가지만 455행이라 비용이 없다. 실측(테스트컨테이너)에서 한 호출이
     * 3~21ms 였고, 키워드 2개짜리가 1개짜리보다 특별히 느리지도 않았다.
     *
     * <p>엔티티가 아니라 {@link ConceptCandidate} 로 돌려준다. 검색은 앵커를 <b>고르는</b> 단계라
     * 본문이 필요 없고, 엔티티를 내보내면 호출부가 후보 5건의 설명까지 프롬프트에 실을 수 있다.
     */
    public List<ConceptCandidate> searchConcepts(List<String> keywords, int limit) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }

        List<String> usable = keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .toList();

        Map<Long, ChatConcept> merged = new LinkedHashMap<>();
        for (String keyword : usable) {
            chatConceptRepository.searchByNameRanked(keyword, limit)
                    .forEach(concept -> merged.putIfAbsent(concept.getId(), concept));
        }

        return merged.values().stream()
                .sorted(Comparator.comparingInt((ChatConcept concept) -> matchGrade(concept.getName(), usable))
                        .thenComparingInt(concept -> exactKeywordIndex(concept.getName(), usable))
                        .thenComparingInt(concept -> concept.getName().length())
                        .thenComparing(ChatConcept::getId))
                .limit(limit)
                .map(ConceptCandidate::from)
                .toList();
    }

    /**
     * 병합 후 순위. 리포지토리 쿼리의 {@code ORDER BY} 와 같은 기준을 자바에서 다시 적용한다.
     *
     * <p>완전일치 → 접미일치 → 접두일치 → 나머지. 접미를 접두보다 위에 두는 근거는
     * {@code ChatConceptRepository.searchByNameRanked} 주석에 있다.
     */
    private static int matchGrade(String name, List<String> keywords) {
        String lowerName = name.toLowerCase();
        int grade = 3;
        for (String keyword : keywords) {
            String lowerKeyword = keyword.toLowerCase();
            if (lowerName.equals(lowerKeyword)) {
                return 0;
            }
            if (lowerName.endsWith(lowerKeyword)) {
                grade = Math.min(grade, 1);
            } else if (lowerName.startsWith(lowerKeyword)) {
                grade = Math.min(grade, 2);
            }
        }
        return grade;
    }

    /**
     * 서로 다른 개념이 각각 다른 키워드에 완전일치할 때, <b>앞선 키워드</b>에 맞은 쪽을 위로 올린다.
     *
     * <p>이게 없으면 이름이 짧은 쪽이 이겨서 엉뚱한 앵커가 잡힌다. 실측으로 걸린 사례 —
     * {@code [소인수분해, 약수]} 는 {@code 약수}(2자)가, {@code [이항, 항]} 은 {@code 항}(1자)이
     * 이겼다. 셋 다 실재하는 개념명이라 완전일치끼리 부딪힌 것이다.
     *
     * <p>등급을 넘나드는 순서 독립성은 그대로다 — 뒤쪽 키워드의 완전일치는 여전히 앞쪽 키워드의
     * 부분일치를 이긴다. 순서는 <b>똑같이 좋은 후보들 사이</b>에서만 쓴다. 추출 프롬프트가
     * "질문에 있는 말을 첫 번째로" 지시하므로, 앞선 키워드가 곧 학생이 실제로 쓴 말이다.
     */
    private static int exactKeywordIndex(String name, List<String> keywords) {
        String lowerName = name.toLowerCase();
        for (int i = 0; i < keywords.size(); i++) {
            if (lowerName.equals(keywords.get(i).toLowerCase())) {
                return i;
            }
        }
        return keywords.size();
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

    /**
     * 앵커에서 한 칸 내려간 개념을 돌려준다. 하향 탐색의 기본 이동이다.
     *
     * <p>선수가 없으면 빈 결과다 — 예외가 아니라 "갈 곳 없음"이라는 정상 경로이며, 중1 210개 중
     * 19개가 여기 해당한다. 존재하지 않는 {@code conceptId} 도 같은 이유로 빈 결과다.
     * 이 계층에서 예외를 던지면 {@code GlobalExceptionHandler} 에 해당 핸들러가 없어 500 으로 샌다.
     */
    public Optional<ConceptView> findNextStepDown(Long conceptId) {
        if (conceptId == null) {
            return Optional.empty();
        }
        return chatConceptRepository.findNextStepDownId(conceptId)
                .flatMap(chatConceptRepository::findById)
                .map(ConceptView::from);
    }

    /**
     * 앵커에서 가장 가까운 초등 개념으로 건너뛴다. 상한 안에서 못 찾으면 빈 결과다.
     *
     * <p>{@code maxDepth} 는 <b>거부하지 않고 범위 안으로 누른다.</b> 이 값은 학생 입력이 아니라
     * 호출부가 주는 성능 보호 장치라, 범위를 벗어났다고 대화를 끊을 이유가 없다. 예외를 던지면
     * 500 으로 새기도 한다. 같은 이유로 {@code ConceptTools} 도 LLM 이 준 인자를 클램프한다.
     */
    public Optional<ConceptLanding> findNearestElementary(Long conceptId, int maxDepth) {
        if (conceptId == null) {
            return Optional.empty();
        }
        int depth = Math.clamp(maxDepth, MIN_LANDING_DEPTH, MAX_LANDING_DEPTH);

        return chatConceptRepository.findNearestElementary(conceptId, depth)
                .flatMap(hop -> chatConceptRepository.findById(hop.getId())
                        .map(concept -> new ConceptLanding(
                                ConceptView.from(concept, hop.getHop()), hop.getHop())));
    }

    /** 기본 상한으로 건너뛴다. */
    public Optional<ConceptLanding> findNearestElementary(Long conceptId) {
        return findNearestElementary(conceptId, DEFAULT_LANDING_DEPTH);
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
        List<ConceptCandidate> searched = searchConcepts(keywords, DEFAULT_SEARCH_LIMIT);

        if (searched.isEmpty()) {
            return subUnitConceptNames.isEmpty()
                    ? ConceptContext.noEvidence()
                    : ConceptContext.of(null, List.of(), subUnitConceptNames);
        }

        // 확장 결과의 첫 원소가 앵커 자신이다. 재귀 CTE 의 시작점이 hop 0 으로 들어오고
        // hop 오름차순으로 정렬되기 때문이다. 앵커 본문을 따로 조회하지 않는 이유다.
        List<ConceptView> concepts = expandPrereqs(searched.get(0).id(), depth, elemHopMax);
        ConceptView anchor = concepts.isEmpty() ? null : concepts.get(0);
        return ConceptContext.of(anchor, concepts, subUnitConceptNames);
    }

    /** 기본 깊이와 기본 초등 상한으로 근거를 만든다. */
    public ConceptContext buildContext(Long subUnitId, List<String> keywords) {
        return buildContext(subUnitId, keywords, DEFAULT_DEPTH, DEFAULT_ELEM_HOP_MAX);
    }
}
