-- STEP_FILL 단계 텍스트에서 KaTeX 구분자 없이 저장된 대표적인 제곱 표현을 보정한다.
-- BLANK, ANSWER_REF 세그먼트는 변경하지 않고 TEXT.value만 대상으로 한다.

WITH normalized AS (
    SELECT
        step.id,
        jsonb_agg(
            CASE
                WHEN segment.value ->> 'type' <> 'TEXT'
                    OR segment.value ->> 'value' IS NULL
                THEN segment.value
                ELSE jsonb_set(
                    segment.value,
                    '{value}',
                    to_jsonb(
                        replace(
                            replace(
                                replace(
                                    replace(
                                        segment.value ->> 'value',
                                        'km^2',
                                        E'$\\mathrm{km}^2$'
                                    ),
                                    'cm^2',
                                    E'$\\mathrm{cm}^2$'
                                ),
                                'm^2',
                                E'$\\mathrm{m}^2$'
                            ),
                            'πr^2',
                            E'$\\pi r^2$'
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
  AND step.segments::text ~ '(cm|km|m)\^2|πr\^2';
