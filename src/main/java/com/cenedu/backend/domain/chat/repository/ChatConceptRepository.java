package com.cenedu.backend.domain.chat.repository;

import com.cenedu.backend.domain.chat.entity.ChatConcept;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 개념 조회. 개념 챗봇의 도구 3종(이름 검색 / 선수 확장 / 소단원 목록)이 쓰는 DB 계층이다.
 *
 * <p><b>예외 — 검토 완료, 팀 합의 대기.</b> 아래 PostgreSQL 네이티브 쿼리 4개는 AGENTS.md 6절의
 * "네이티브 쿼리는 pgvector 유사도 검색만 허용" 규칙 밖에 있다. <b>JPQL 대체 가능성을 검토했고
 * 불가능하다</b> — 선수 개념 그래프를 따라 올라가는 <b>재귀 CTE</b> 는 JPQL 에 대응 문법이 없고,
 * 애플리케이션에서 반복 조회로 풀면 깊이만큼 쿼리가 늘어 그 자리에서 N+1 이 된다.
 *
 * <p>따라서 남는 선택은 규칙에 예외를 적는 것뿐인데, <b>AGENTS.md 는 팀 공유 문서라 합의 없이
 * 고치지 않는다.</b> 문안을 준비해 팀에 올린 상태이며, 합의되면 이 주석은 그 조항을 가리키도록
 * 바꾼다.
 *
 * <p><b>그때까지 지키는 것.</b>
 * <ul>
 *   <li>네이티브 쿼리를 <b>이 Repository 밖으로 늘리지 않는다.</b> 개념 그래프 탐색에 한정한다</li>
 *   <li>재귀 CTE 는 깊이 상한을 쿼리 안에 둔다 — 사이클이 있으면 무한히 돈다</li>
 *   <li>파라미터를 문자열로 이어 붙이지 않는다. 바인딩만 쓴다</li>
 *   <li>쿼리마다 통합 테스트로 결과를 고정한다. 네이티브라 컴파일이 잡아 주지 않는다</li>
 * </ul>
 */
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

    /**
     * 앵커에서 <b>한 칸</b> 내려간 개념 하나를 돌려준다. 하향 탐색의 기본 이동이다.
     *
     * <p>직접 선수(1홉)만 보므로 재귀가 필요 없다. 정렬은 <b>학년값 내림차순, id 오름차순</b>이며,
     * 학년값이 클수록 학생에게 가까운 개념이라 "가장 가까운 것부터" 내려간다. 분기하지 않고
     * 하나만 고른다 — 갈래를 늘리면 근거가 통째로 바뀌어 되돌리기 어렵다.
     *
     * <p><b>선수가 0개면 빈 결과다.</b> 예외가 아니라 정상 경로다. 중1 210개 중 19개가 여기 해당하며
     * 그 개념들은 그래프에 내려갈 곳이 없다.
     *
     * <p>학년값을 컬럼으로 두지 않고 쿼리에서 계산하는 이유는 {@code source_semester} 가 원천
     * 기록이기 때문이다. 전처리로 중1에 편입된 개념 4건이 원천 학년을 그대로 갖고 있어
     * <b>{@code grade_band} 가 우선</b>이며, 중1이면 학기를 읽지 않고 14로 고정한다.
     *
     * <p><b>{@code NULLS LAST} 가 있어야 한다.</b> PostgreSQL 은 {@code DESC} 에서 NULL 을 먼저
     * 놓는다. 학기 파싱이 실패해 학년값이 NULL 이 되면 그 개념이 <b>항상 1순위</b>가 된다.
     * 현재 데이터에 파싱 실패는 0건이지만, 데이터가 늘면 조용히 틀린 답이 나오는 경로다.
     */
    @Query(value = """
            SELECT p.id
            FROM chat_concept_prereq e
            JOIN chat_concept p ON p.id = e.prereq_concept_id
            WHERE e.concept_id = :conceptId
            ORDER BY
                CASE WHEN p.grade_band = 'MIDDLE_1' THEN 14
                     ELSE (substring(p.source_semester from '초([1-6])-')::int - 1) * 2
                          + substring(p.source_semester from '-([12])학기$')::int
                END DESC NULLS LAST,
                p.id ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Long> findNextStepDownId(@Param("conceptId") Long conceptId);

    /**
     * 앵커에서 <b>가장 가까운 초등 개념</b>과 그 거리를 돌려준다. 대역 건너뛰기의 착지점이다.
     *
     * <p>{@link #findPrereqClosure} 와 같은 {@code path} 배열 순환 방어를 쓰되 두 가지가 다르다.
     * 첫째 {@code elem_hop} 필터가 없다 — 착지는 상한 없이 가장 가까운 초등 노드를 찾는 것이다.
     * 둘째 초등 노드만 남기고 <b>최소 홉 하나</b>만 고른다.
     *
     * <p><b>1순위를 반복 적용하지 않는 이유.</b> 두 기능이 답하는 질문이 다르다 — 한 칸 내려가기는
     * "바로 아래 무엇이 있나"이고 건너뛰기는 "가장 가까운 초등 개념이 무엇인가"다. 1순위만 따라가면
     * 선수가 0개인 중1 개념으로 들어가 막히는데, 실측상 210개 중 48개가 그렇게 막혔고 그중 38개는
     * 분기를 허용하면 중앙 2홉에 닿았다. 길이 멀어서가 아니라 한 갈래만 봐서 놓친 것이다.
     *
     * <p>같은 홉에 초등 후보가 여럿이면 {@link #findNextStepDownId} 와 같은 정렬로 하나를 고른다.
     * 그래야 두 기능의 선택 기준이 어긋나지 않는다.
     *
     * @param conceptId 앵커 개념
     * @param maxDepth  탐색 깊이 상한. 이 안에서 못 찾으면 빈 결과다
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
                WHERE w.hop < :maxDepth
                  AND NOT (p.id = ANY(w.path))
            )
            SELECT w.id AS id, MIN(w.hop) AS hop
            FROM walk w
            JOIN chat_concept c ON c.id = w.id
            WHERE c.grade_band = 'ELEMENTARY'
            GROUP BY w.id, c.id
            ORDER BY
                MIN(w.hop) ASC,
                CASE WHEN c.grade_band = 'MIDDLE_1' THEN 14
                     ELSE (substring(c.source_semester from '초([1-6])-')::int - 1) * 2
                          + substring(c.source_semester from '-([12])학기$')::int
                END DESC NULLS LAST,
                w.id ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ConceptHop> findNearestElementary(@Param("conceptId") Long conceptId,
                                               @Param("maxDepth") int maxDepth);

    /** 확장 결과 한 줄. hop 0 은 앵커 자신이다. */
    interface ConceptHop {
        Long getId();

        Integer getHop();
    }
}
