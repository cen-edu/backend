package com.cenedu.backend.domain.chat.repository;

import com.cenedu.backend.domain.chat.entity.ChatConcept;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 개념 조회. 개념 챗봇의 도구 3종(이름 검색 / 선수 확장 / 소단원 목록)이 쓰는 DB 계층이다. */
public interface ChatConceptRepository extends JpaRepository<ChatConcept, Long> {

    /**
     * 이름에 키워드가 포함된 개념을 455개 전체에서 찾는다. 소단원 경계를 넘는 유일한 경로다.
     *
     * <p>정렬이 붙기 전에는 순서가 DB 물리 순서에 좌우됐다. 그 탓에 {@code 항} 을 검색하면
     * {@code 다항식}·{@code 단항식} 이 앞을 채워 정작 {@code 항} 이 상위 5건 밖으로 밀렸다.
     * 상위는 첫 건을 앵커로 쓰므로 이 순서가 곧 답의 근거를 정한다.
     *
     * <p><b>접미 일치를 접두 일치보다 위에 둔다.</b> 한국어 개념명은 핵심 명사가 뒤에 온다 —
     * {@code 도수분포표}·{@code 공약수와 최대공약수} 가 그렇다. 반대로 키워드로 시작하는 이름은
     * 대개 다른 개념이다({@code 표와 그래프로 나타내기}, {@code 최대공약수의 활용}).
     * 실측으로 확인했다: 접두를 먼저 두면 task_05 대비 D4·I5 두 건이 나빠지고, 뒤집으면 악화가 없다.
     *
     * <p>같은 등급 안에서는 이름이 짧은 것을 먼저 둔다. 짧을수록 그 개념 자신일 가능성이 높다.
     * 마지막 키를 {@code id} 로 고정하는 것은 동점에서 순서가 흔들리면 측정이 재현되지 않기 때문이다.
     *
     * <p>{@code ILIKE} 를 쓰므로 등급 판정도 {@code lower()} 로 맞춘다. 대소문자가 섞이면
     * 검색은 잡고 정렬은 못 알아보는 어긋남이 생긴다.
     */
    @Query(value = """
            SELECT c.*
            FROM chat_concept c
            WHERE c.name ILIKE '%' || :keyword || '%'
            ORDER BY
                CASE WHEN lower(c.name) = lower(:keyword) THEN 0
                     WHEN lower(c.name) LIKE '%' || lower(:keyword) THEN 1
                     WHEN lower(c.name) LIKE lower(:keyword) || '%' THEN 2
                     ELSE 3 END,
                length(c.name),
                c.id
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatConcept> searchByNameRanked(@Param("keyword") String keyword, @Param("limit") int limit);

    /** 한 소단원에 속한 개념 전체를 돌려준다. 개념이 0건인 소단원이 하나 있어 빈 목록이 나올 수 있다. */
    List<ChatConcept> findBySubUnitIdOrderByIdAsc(Long subUnitId);

    /**
     * 앵커에서 선수 방향으로 재귀 확장한 개념 id 와 최단 거리(hop)를 돌려준다.
     *
     * <p>본문을 함께 돌려주지 않고 id 만 주는 이유는, 네이티브 쿼리의 인터페이스 프로젝션이
     * 밑줄이 든 컬럼({@code grade_band} 등)의 별칭 매핑을 환경에 따라 다르게 처리하기
     * 때문이다. 단어 하나짜리 컬럼 두 개만 받고 본문은 {@code findAllById} 로 읽는다.
     * 결과가 보통 수십 개 이내라 두 번 읽는 비용도 문제되지 않는다.
     *
     * <p>순환 방어는 {@code path} 배열이 한다. hop 을 결과에 넣는 순간 {@code UNION} 의
     * 중복 제거만으로는 순환이 막히지 않는다 — (A,0)·(B,1)·(A,2) 가 전부 다른 행이라
     * 접히지 않기 때문이다. 원천에 상호참조 21쌍이 있어 실제로 걸리는 문제이며, 경로에 이미
     * 나온 개념을 배제해 depth 와 무관하게 종료를 보장한다.
     *
     * @param conceptId  앵커 개념
     * @param depth      확장 깊이
     * @param elemHopMax 주입할 초등 개념의 elem_hop 상한. 기본 1
     */
    @Query(value = """
            WITH RECURSIVE walk (id, hop, path) AS (
                SELECT c.id, 0, ARRAY[c.id]
                FROM chat_concept c
                WHERE c.id = :conceptId
              UNION ALL
                SELECT p.id, w.hop + 1, w.path || p.id
                FROM walk w
                JOIN chat_concept_prereq e ON e.concept_id = w.id
                JOIN chat_concept p ON p.id = e.prereq_concept_id
                WHERE w.hop < :depth
                  AND NOT (p.id = ANY(w.path))
                  AND (p.grade_band = 'MIDDLE_1' OR p.elem_hop <= :elemHopMax)
            )
            SELECT w.id AS id, MIN(w.hop) AS hop
            FROM walk w
            GROUP BY w.id
            ORDER BY MIN(w.hop), w.id
            """, nativeQuery = true)
    List<ConceptHop> findPrereqClosure(@Param("conceptId") Long conceptId,
                                       @Param("depth") int depth,
                                       @Param("elemHopMax") short elemHopMax);

    /** 확장 결과 한 줄. hop 0 은 앵커 자신이다. */
    interface ConceptHop {
        Long getId();

        Integer getHop();
    }
}
