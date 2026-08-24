-- =============================================================================
-- 문항 생성 성공률·실패 사유 진단 (객관식/서술형/빈칸형 "잘 생성되는지" 정량화)
-- =============================================================================
-- 목적: AI 생성이 유형별로 얼마나 자주 성공/실패하는지, 실패는 어느 단계·사유인지
--       기존 데이터(problem_generation_item, problem_authoring_version)로 측정한다.
--       신규 LLM 호출 없이 과거 실행 이력만으로 동작한다. 읽기 전용.
--
-- 실행: psql "$DATABASE_URL" -f scripts/diagnose-generation-outcomes.sql
--
-- 단계 구분:
--   GENERATION 단계 실패(GENERATION_FAILED) = 생성물이 구조/정규화 검증에서 탈락
--       (버전이 만들어지기 전이라 DB엔 상세가 없고 애플리케이션 로그에만 남는다).
--   VERIFICATION 단계 실패(CANDIDATE_INVALID / 검증 FAILED) = 생성은 통과했으나
--       solver·정합·루브릭 검사에서 탈락 (problem_authoring_version.verification_report에 상세).
-- =============================================================================

\echo '=== [1] AI 생성 유형별 성공률 ==='
SELECT
  gi.generation_command->'specification'->>'questionType' AS qtype,
  count(*)                                                 AS total,
  count(*) FILTER (WHERE status='SUCCEEDED')              AS succeeded,
  count(*) FILTER (WHERE status='FAILED')                 AS failed,
  round(100.0 * count(*) FILTER (WHERE status='SUCCEEDED')
        / NULLIF(count(*),0), 1)                          AS success_pct,
  round(avg(retry_count),2)                               AS avg_retry
FROM problem_generation_item gi
WHERE gi.slot_source = 'AI_GENERATION'
GROUP BY 1
ORDER BY success_pct;

\echo '=== [2] 실패 아이템의 종료 사유(단계 구분) ==='
SELECT last_error_code, count(*) AS n
FROM problem_generation_item
WHERE slot_source = 'AI_GENERATION' AND status = 'FAILED'
GROUP BY 1 ORDER BY 2 DESC;

\echo '=== [3] 목적(일반/종합/맞춤)별 성공/실패 ==='
SELECT generation_purpose, status, count(*) AS n
FROM problem_generation_item
WHERE slot_source = 'AI_GENERATION'
GROUP BY 1,2 ORDER BY 1,2;

\echo '=== [4] 검증 단계 실패 사유 집계 (problem_authoring_version) ==='
-- 생성은 통과했으나 검증에서 떨어진 후보의 실제 finding.
-- message 를 짧게 잘라 유형별로 묶는다(칸 번호 등 변수는 남을 수 있음).
SELECT
  f->>'checkType' AS check_type,
  f->>'code'      AS code,
  left(f->>'message', 60) AS message,
  count(*)        AS n
FROM problem_authoring_version v,
     LATERAL jsonb_array_elements(
        v.verification_report->'contentReport'->'findings') f
WHERE v.verification_status IN ('FAILED','ERROR')
  AND f->>'status' IN ('FAIL','ERROR')
GROUP BY 1,2,3
ORDER BY n DESC
LIMIT 30;

\echo '=== [5] 검증 단계 실패를 카테고리로 묶은 요약 ==='
SELECT
  f->>'checkType' AS check_type,
  f->>'code'      AS code,
  count(*)        AS n
FROM problem_authoring_version v,
     LATERAL jsonb_array_elements(
        v.verification_report->'contentReport'->'findings') f
WHERE v.verification_status IN ('FAILED','ERROR')
  AND f->>'status' IN ('FAIL','ERROR')
GROUP BY 1,2
ORDER BY n DESC;

\echo '=== [6] 버전 검증 통과율 (생성 통과분 중) ==='
SELECT verification_status, count(*) AS n
FROM problem_authoring_version
GROUP BY 1 ORDER BY 2 DESC;

-- =============================================================================
-- 해석 가이드
--   [1] success_pct 가 낮은 유형 = 생성이 특히 불안정한 유형.
--   [2] GENERATION_FAILED 비중이 크면 실패의 다수가 "생성물 구조검증 탈락"이고,
--       그 상세 사유는 DB에 없으므로 애플리케이션 로그
--       (event=problem_authoring_stage operation=GENERATION outcome=ERROR message=...)
--       를 봐야 한다.
--   [4][5] 검증 단계 실패는 여기서 사유가 다 보인다. 상위 사유가 프롬프트 개선
--       포인트를 직접 가리킨다(예: 해설↔정답 불일치, 개념안내의 정답 노출 등).
-- =============================================================================
