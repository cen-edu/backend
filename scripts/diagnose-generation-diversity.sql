-- =============================================================================
-- 생성 문항 반복성(다양성) 진단 쿼리
-- =============================================================================
-- 목적: "유형을 고르면 계속 같은 문제만 나온다"는 체감을 데이터로 정량화한다.
--
-- 핵심 아이디어: 발문(prompt_text)에서 숫자를 전부 '#'로 마스킹한 "구조 키"로
--   문항을 묶으면, 숫자만 다르고 구조·맥락이 같은 문항이 한 그룹으로 모인다.
--   (이 마스킹은 Java의 duplicate_cluster_key 계산 방식을 SQL로 근사한 것이다:
--    ProblemSearchDocumentFactory.create() 의 `normalizedPrompt.replaceAll(NUMBER,'#')`.)
--
-- 이 파일은 RAG 검색 인덱스(problem_search_index)에 의존하지 않는다 —
--   problem_question.prompt_text 만으로 동작하므로 인덱싱이 꺼져 있어도 쓸 수 있다.
--   맨 아래 [부록]은 인덱스가 채워져 있을 때의 교차 확인용이다.
--
-- 실행: psql "$DATABASE_URL" -f scripts/diagnose-generation-diversity.sql
--   (읽기 전용. 데이터를 수정하지 않는다.)
-- =============================================================================

\echo '=== [0] 한 줄 요약: 생성 문항 중 구조 중복 비율 ==='
-- overall_variety_ratio = 서로 다른 구조 수 / 전체 생성 수 (1.0 = 전부 고유, 낮을수록 반복 심함)
-- pct_structural_duplicates = 다른 생성물과 구조가 겹치는 문항의 비율(%)
WITH generated AS (
    SELECT
        pq.id,
        btrim(regexp_replace(
            regexp_replace(lower(pq.prompt_text), '[0-9]+([.,][0-9]+)?', '#', 'g'),
            '\s+', ' ', 'g')) AS structure_key
    FROM problem_question pq
    WHERE pq.source_type = 'GENERATED'
      AND pq.deleted_at IS NULL
)
SELECT
    COUNT(*)                                                    AS total_generated,
    COUNT(DISTINCT structure_key)                              AS distinct_structures,
    ROUND(COUNT(DISTINCT structure_key)::numeric
          / NULLIF(COUNT(*), 0), 3)                            AS overall_variety_ratio,
    ROUND(100.0 * (COUNT(*) - COUNT(DISTINCT structure_key))
          / NULLIF(COUNT(*), 0), 1)                            AS pct_structural_duplicates
FROM generated;


\echo '=== [A] 버킷(소단원 x 유형 x 난이도)별 구조 다양성 (반복 심한 순) ==='
-- variety_ratio 가 1.0 에 가까우면 다양, 0 에 가까우면 같은 구조만 반복.
-- structural_duplicates = 그 버킷에서 "다른 것과 구조가 겹치는" 문항 수.
WITH generated AS (
    SELECT
        pq.sub_unit_id, pq.question_type, pq.difficulty,
        btrim(regexp_replace(
            regexp_replace(lower(pq.prompt_text), '[0-9]+([.,][0-9]+)?', '#', 'g'),
            '\s+', ' ', 'g')) AS structure_key
    FROM problem_question pq
    WHERE pq.source_type = 'GENERATED'
      AND pq.deleted_at IS NULL
)
SELECT
    sub_unit_id, question_type, difficulty,
    COUNT(*)                                    AS total_generated,
    COUNT(DISTINCT structure_key)               AS distinct_structures,
    COUNT(*) - COUNT(DISTINCT structure_key)    AS structural_duplicates,
    ROUND(COUNT(DISTINCT structure_key)::numeric
          / NULLIF(COUNT(*), 0), 3)             AS variety_ratio
FROM generated
GROUP BY sub_unit_id, question_type, difficulty
HAVING COUNT(*) >= 3            -- 표본이 최소 3개인 버킷만
ORDER BY variety_ratio ASC, total_generated DESC
LIMIT 40;


\echo '=== [B] 가장 많이 반복된 구조 클러스터 Top 30 (샘플 발문 포함) ==='
-- 같은 structure_key 로 묶인 문항이 많을수록 "같은 문제 다른 숫자"가 대량 생성된 것.
WITH generated AS (
    SELECT
        pq.id, pq.sub_unit_id, pq.question_type, pq.difficulty, pq.prompt_text,
        btrim(regexp_replace(
            regexp_replace(lower(pq.prompt_text), '[0-9]+([.,][0-9]+)?', '#', 'g'),
            '\s+', ' ', 'g')) AS structure_key
    FROM problem_question pq
    WHERE pq.source_type = 'GENERATED'
      AND pq.deleted_at IS NULL
)
SELECT
    sub_unit_id, question_type, difficulty,
    COUNT(*)                          AS problems_in_cluster,
    MIN(id)                           AS sample_question_id,
    LEFT(MIN(prompt_text), 120)       AS sample_prompt
FROM generated
GROUP BY sub_unit_id, question_type, difficulty, structure_key
HAVING COUNT(*) >= 3
ORDER BY problems_in_cluster DESC
LIMIT 30;


\echo '=== [C] 완전 동일 발문(숫자까지 같음) - 강한 적신호 ==='
-- 구조뿐 아니라 숫자까지 같은 문항이 2개 이상이면 사실상 복제.
SELECT
    sub_unit_id, question_type, difficulty,
    COUNT(*)                     AS identical_count,
    MIN(id)                      AS sample_question_id,
    LEFT(MIN(prompt_text), 120)  AS sample_prompt
FROM problem_question
WHERE source_type = 'GENERATED'
  AND deleted_at IS NULL
GROUP BY sub_unit_id, question_type, difficulty,
         btrim(regexp_replace(lower(prompt_text), '\s+', ' ', 'g'))
HAVING COUNT(*) > 1
ORDER BY identical_count DESC
LIMIT 30;


\echo '=== [D] 은행 재고가 얕은 버킷 (은행 경로 반복 원인) ==='
-- 조건에 맞는 은행 문항이 몇 개 없으면, 무작위 셔플을 해도 매번 같은 소수만 나온다.
-- reusable_count 가 작은 버킷이 "은행에서도 같은 게 나오는" 구간이다.
SELECT
    sub_unit_id, difficulty, question_type,
    COUNT(*) AS reusable_count
FROM problem_question
WHERE deleted_at IS NULL
  AND source_type <> 'RUNTIME'    -- 은행 후보(수입 + 확정 생성)
GROUP BY sub_unit_id, difficulty, question_type
HAVING COUNT(*) < 5
ORDER BY reusable_count ASC, sub_unit_id
LIMIT 40;


-- =============================================================================
-- [부록] RAG 검색 인덱스가 채워져 있을 때의 교차 확인 (선택)
--   problem_search_index.duplicate_cluster_key 는 서버가 계산한 정본 구조 키다.
--   위 SQL 근사와 결과가 크게 다르면, 정본 키 기준이 더 정확하다.
--   인덱스가 비어 있으면(인덱싱 비활성) 아래는 0행이다.
-- =============================================================================
\echo '=== [E] (부록) 정본 duplicate_cluster_key 기준 버킷별 다양성 ==='
SELECT
    psi.sub_unit_id, psi.question_type, psi.difficulty,
    COUNT(*)                                          AS total_generated,
    COUNT(DISTINCT psi.duplicate_cluster_key)         AS distinct_structures,
    ROUND(COUNT(DISTINCT psi.duplicate_cluster_key)::numeric
          / NULLIF(COUNT(*), 0), 3)                   AS variety_ratio
FROM problem_search_index psi
JOIN problem_question pq ON pq.id = psi.question_id
WHERE pq.source_type = 'GENERATED'
  AND pq.deleted_at IS NULL
GROUP BY psi.sub_unit_id, psi.question_type, psi.difficulty
HAVING COUNT(*) >= 3
ORDER BY variety_ratio ASC, total_generated DESC
LIMIT 40;

-- =============================================================================
-- 해석 가이드
--   [0] overall_variety_ratio 가 예: 0.5 이면 생성물의 절반이 다른 생성물과
--       구조가 겹친다는 뜻 → 반복 체감이 데이터로 확인됨.
--   [A] variety_ratio 낮은 버킷 = 특정 유형·소단원에서 특히 반복 심함.
--   [B] problems_in_cluster 큰 클러스터 = "이 구조가 계속 나온다"의 실체.
--   [C] identical_count > 1 = 숫자까지 같은 복제(가장 심각).
--   [D] reusable_count 작음 = 은행 재고 부족이 원인인 버킷(생성 다양성과 무관).
--   → 원인이 (생성 다양성 부족)인지 (은행 재고 부족)인지 [A~C] vs [D] 로 분리된다.
-- =============================================================================
