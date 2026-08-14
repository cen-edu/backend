-- 직전 정규화에서 생성될 수 있는 중복 백슬래시를 KaTeX 명령어 형태로 정리한다.

WITH normalized AS (
    SELECT step.id,
           jsonb_agg(
               CASE WHEN segment.value->>'type' <> 'TEXT' THEN segment.value
                    ELSE jsonb_set(
                        segment.value,
                        '{value}',
                        to_jsonb(replace(
                            segment.value->>'value',
                            '$' || chr(92) || chr(92) || 'text',
                            '$' || chr(92) || 'text'
                        ))
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
