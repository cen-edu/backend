-- 정답 원문을 기준으로 채점용 정규화 값을 다시 생성한다.
-- answer_raw는 화면 표시용이므로 보존하고 answer_normalized만 수정한다.

WITH source_answers AS (
    SELECT
        id,
        regexp_replace(answer_raw, chr(92) || chr(92), chr(92), 'g') AS value
    FROM problem_answer_unit
    WHERE answer_raw IS NOT NULL
      AND position(chr(92) IN answer_normalized) > 0
), normalized AS (
    SELECT
        id,
        regexp_replace(
            regexp_replace(
                regexp_replace(
                    regexp_replace(
                        regexp_replace(
                            regexp_replace(
                                regexp_replace(
                                    regexp_replace(
                                        regexp_replace(
                                            regexp_replace(
                                                value,
                                                chr(92) || chr(92) || 'frac' || chr(123) || '([^' || chr(123) || chr(125) || ']+)' || chr(125) || chr(123) || '([^' || chr(123) || chr(125) || ']+)' || chr(125),
                                                chr(92) || '1/' || chr(92) || '2',
                                                'g'
                                            ),
                                            chr(92) || chr(92) || 'times',
                                            '*',
                                            'g'
                                        ),
                                        chr(92) || chr(92) || 'cdot',
                                        '*',
                                        'g'
                                    ),
                                    chr(92) || chr(92) || 'pi',
                                    'pi',
                                    'g'
                                ),
                                chr(94) || chr(123) || chr(92) || chr(92) || 'circ' || chr(125),
                                'deg',
                                'g'
                            ),
                            chr(92) || chr(92) || 'quad',
                            '',
                            'g'
                        ),
                        chr(92) || chr(92) || 'left',
                        '',
                        'g'
                    ),
                    chr(92) || chr(92) || 'text' || chr(123) || '([^' || chr(123) || chr(125) || ']*)' || chr(125),
                    '\\1',
                    'g'
                ),
                chr(92) || chr(92) || 'begin' || chr(123) || '[^' || chr(123) || chr(125) || ']+' || chr(125),
                '',
                'g'
            ),
            chr(92) || chr(92),
            '',
            'g'
        ) AS value
    FROM source_answers
)
UPDATE problem_answer_unit AS answer_unit
SET answer_normalized = btrim(
    regexp_replace(
        replace(
            replace(
                replace(
                    replace(
                        replace(
                            replace(normalized.value, chr(92) || 'begin{array}{l}', ''),
                            chr(92) || 'end{array}', ''
                        ),
                        chr(92) || 'angle', 'angle '
                    ),
                    chr(94) || chr(123) || chr(92) || 'circ' || chr(125), 'degree'
                ),
                chr(92) || 'circ', 'circ'
            ),
            chr(92) || chr(92), ''
        ),
        '([0-9)])pi', chr(92) || '1*pi', 'g'
    )
)
FROM normalized
WHERE answer_unit.id = normalized.id;
