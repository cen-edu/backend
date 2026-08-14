-- 수식 구분자 없이 저장된 괄호·변수·숫자 거듭제곱을 TEXT 세그먼트 단위로 보정한다.

WITH normalized AS (
    SELECT step.id,
           jsonb_agg(
               CASE
                   WHEN segment.value->>'type' <> 'TEXT'
                        OR segment.value->>'value' IS NULL
                        OR segment.value->>'value' LIKE '%$%'
                   THEN segment.value
                   ELSE jsonb_set(
                       segment.value,
                       '{value}',
                       to_jsonb(
                           regexp_replace(
                               replace(segment.value->>'value', '반지름^2', '$\\text{반지름}^2$'),
                               '(\\([^)]*\\)|[A-Za-z0-9]+)\\^([A-Za-z0-9]+|\\{[^}]+\\})',
                               E'$\\1^\\2$',
                               'g'
                           )
                       )
                   )
               END ORDER BY segment.ordinality
           ) AS segments
    FROM problem_step step
    CROSS JOIN LATERAL jsonb_array_elements(step.segments)
        WITH ORDINALITY AS segment(value, ordinality)
    JOIN problem_question question ON question.id = step.question_id
    WHERE question.question_type = 'STEP_FILL'
    GROUP BY step.id
)
UPDATE problem_step step
SET segments = normalized.segments
FROM normalized
WHERE step.id = normalized.id;
