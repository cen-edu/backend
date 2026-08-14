-- KaTeX 구분자 밖에 저장된 숫자·영문 변수의 거듭제곱 표현을 보정한다.
-- 이미 수식 구분자를 포함한 TEXT와 BLANK/ANSWER_REF는 변경하지 않는다.

WITH normalized AS (
    SELECT
        step.id,
        jsonb_agg(
            CASE
                WHEN segment.value ->> 'type' <> 'TEXT'
                    OR segment.value ->> 'value' IS NULL
                    OR segment.value ->> 'value' LIKE '%$%'
                THEN segment.value
                ELSE jsonb_set(
                    segment.value,
                    '{value}',
                    to_jsonb(
                        regexp_replace(
                            regexp_replace(
                                segment.value ->> 'value',
                                '([0-9]+)\^([0-9]+)',
                                E'$\\1^\\2$',
                                'g'
                            ),
                            '([A-Za-z])\^([0-9]+)',
                            E'$\\1^\\2$',
                            'g'
                        )
                    )
                )
            END
            ORDER BY segment.ordinality
        ) AS segments
    FROM problem_step step
    CROSS JOIN LATERAL jsonb_array_elements(step.segments)
        WITH ORDINALITY AS segment(value, ordinality)
    JOIN problem_question question
        ON question.id = step.question_id
    WHERE question.question_type = 'STEP_FILL'
    GROUP BY step.id
)
UPDATE problem_step step
SET segments = normalized.segments
FROM normalized
WHERE step.id = normalized.id
  AND step.segments::text ~ '[A-Za-z0-9]\^[0-9]+';
