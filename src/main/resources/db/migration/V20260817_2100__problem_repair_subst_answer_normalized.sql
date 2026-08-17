-- SUBST 정답의 정규형 교정과 채점 방법 재분류.
--
-- 문제: compare_method='SUBST' 인 2,233건 중 228건(10.2%)이 수식으로 읽히지 않아 자동채점이
-- 전부 FAILED 로 끝난다. 정답이 틀린 것이 아니라 적재기가 만든 answer_normalized 가 깨진 것이다.
-- answer_raw 는 온전한 LaTeX 이므로 거기서 다시 계산한다.
--
--   35^{\circ}        →  answer_normalized 가 '35^{circ}'  (역슬래시 유실)
--   10 \pi ~          →  '~' 는 범위가 아니라 LaTeX 비분리 공백인데 남아 있음
--   324\end{array}    →  표에서 잘라 오며 닫는 태그만 딸려옴
--   <14/5 a>          →  답을 감싼 꺾쇠인데 부등호로 읽힘
--   변 AD, 변 BC      →  한글 답인데 라틴 문자 때문에 SYMBOLIC_EQUIVALENCE 로 분류됨
--
-- 처리 (실측 기준):
--   159건  정규형만 교정하고 SUBST 유지
--    36건  한글이 남는 답 → EXACT (문자열 일치)
--    20건  쉼표로 끊긴 복수답 → SET (집합 비교)
--     7건  한 칸에 답이 2~3개(\begin{array}·줄바꿈) → 손대지 않는다. 어떻게 고쳐도 한쪽 답이
--          버려지므로 교사 수동 채점으로 남긴다
--     6건  범위(155~160)·기호(㉠)처럼 판정 축이 없는 것 → 손대지 않는다
--
-- 멀쩡한 2,005건은 건드리지 않는다 — 대상을 "지금 읽히지 않는 행"으로 한정한다.

-- 되돌릴 수 있도록 변경 전 값을 남긴다. 마이그레이션이라 롤백 스크립트가 없으므로 표로 보관한다.
CREATE TABLE IF NOT EXISTS problem_answer_unit_repair_backup_20260817 (
    id                bigint PRIMARY KEY,
    answer_raw        text,
    answer_normalized text,
    compare_method    varchar(20)
);

INSERT INTO problem_answer_unit_repair_backup_20260817 (id, answer_raw, answer_normalized, compare_method)
SELECT id, answer_raw, answer_normalized, compare_method
FROM problem_answer_unit
WHERE compare_method = 'SUBST'
  AND answer_normalized !~ '^[0-9a-zA-Z+\-*/^().:= ]+$'
ON CONFLICT (id) DO NOTHING;

-- answer_raw 에서 정규형을 다시 만든다. 순서가 중요하다 —
-- 각도(^{\circ}) 를 먼저 떼야 지수 규칙이 그것을 지수로 읽지 않고,
-- 지수({n}) 를 먼저 풀어야 \frac{2 x^{2}}{5} 의 분자에서 중괄호가 사라져 분수 규칙이 매치된다.
CREATE TEMP TABLE subst_repair ON COMMIT DROP AS
WITH target AS (
    SELECT id, answer_raw
    FROM problem_answer_unit
    WHERE compare_method = 'SUBST'
      AND answer_normalized !~ '^[0-9a-zA-Z+\-*/^().:= ]+$'
      -- 한 칸에 답이 여럿인 것은 제외한다(위 주석 참조).
      AND answer_raw !~ '\\begin\{array\}'
      AND answer_raw !~ E'\n'
      -- 숫자 사이의 ~ 는 표시용 공백이 아니라 범위다('155~160'). 지우면 155160 이라는
      -- 엉뚱한 수가 되므로 손대지 않고 FAILED 로 남긴다.
      AND answer_raw !~ '[0-9][[:space:]]*~[[:space:]]*[0-9]'
      -- 값에 단위가 붙은 것('(24-0.006h) °C'). ° 만 떼면 C 가 자유 변수로 읽혀 뜻이 바뀐다.
      AND answer_raw !~ '°C|℃'
)
SELECT id, answer_raw,
       regexp_replace(regexp_replace(regexp_replace(regexp_replace(regexp_replace(regexp_replace(
       regexp_replace(regexp_replace(regexp_replace(regexp_replace(regexp_replace(regexp_replace(
       regexp_replace(regexp_replace(
           answer_raw,
           '^[①-⑮][[:space:]]*', '', 'g'),                              -- 보기 번호 접두
           '\\end\{array\}', '', 'g'),                                   -- 짝 없는 표 종료
           '&', '', 'g'),                                                -- 표 구분자
           '\\(quad|qquad|,|;|:|!)|~', '', 'g'),                         -- 표시용 공백
           '\\(leq|le|geq|ge|neq|lt|gt)([^a-zA-Z]|$)', '<=\2', 'g'),     -- 부등호 명령
           '\\angle[[:space:]]*', '', 'g'),                              -- 각 기호
           '\\overline\{([^{}]*)\}', '\1', 'g'),                         -- 선분 기호
           '(^|[,[:space:]])(점|변|각|모서리|면|선분|호|현)[[:space:]]+', '\1', 'g'),
           '\^\{[[:space:]]*\\circ([[:space:]]*\\prime)?[[:space:]]*\}|°', '', 'g'),
           '\^\{([^{}]*)\}', '^\1', 'g'),                                -- 중괄호 지수
           '\\frac\{([^{}]*)\}\{([^{}]*)\}', '(\1)/(\2)', 'g'),
           '\\(pi)', 'pi', 'g'),
           '\\times|\\cdot', '*', 'g'),
           '[[:space:]]+', '', 'g') AS normalized
FROM target;

-- 부등호 명령 치환은 방향을 구분하지 못하므로(위에서 전부 '<=' 로 갔다) 원문을 보고 되돌린다.
UPDATE subst_repair SET normalized = replace(normalized, '<=', '>=')
 WHERE answer_raw ~ '\\(geq|ge)([^a-zA-Z]|$)';

-- ① 정규형만 교정하고 SUBST 를 유지한다(159건).
UPDATE problem_answer_unit u
   SET answer_normalized = r.normalized
  FROM subst_repair r
 WHERE u.id = r.id
   AND r.normalized ~ '^[0-9a-zA-Z+\-*/^().:=|<>]+$';

-- ② 한글이 남는 답은 식 동치로 채점할 수 없다 → EXACT(36건).
UPDATE problem_answer_unit u
   SET answer_normalized = r.normalized,
       compare_method = 'EXACT'
  FROM subst_repair r
 WHERE u.id = r.id
   AND r.normalized ~ '[가-힣]';

-- ③ 쉼표로 끊긴 복수답은 집합 비교가 맞다 → SET(20건).
UPDATE problem_answer_unit u
   SET answer_normalized = r.normalized,
       compare_method = 'SET'
  FROM subst_repair r
 WHERE u.id = r.id
   AND r.normalized !~ '[가-힣]'
   AND r.normalized !~ '^[0-9a-zA-Z+\-*/^().:=|<>]+$'
   AND r.normalized ~ '^[0-9a-zA-Z+\-*/^().:=|<>,]+$';
