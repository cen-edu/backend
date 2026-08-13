-- 문제은행 데이터 보정
-- 1. 문항 유형별 source_type 정책을 통일한다.
-- 2. ESSAY는 ERD 정책에 따라 모범답안을 저장하지 않는다.
-- 3. STEP_FILL 세그먼트 키와 정답 표현을 ERD 및 채점용 형식에 맞춘다.
-- 4. 원천 데이터로 복원 가능한 객관식 보기와 정답을 보정한다.

-- ESSAY만 RUNTIME이고, 나머지 사전 적재 문항은 IMPORTED이다.
UPDATE problem_question
SET source_type = CASE
    WHEN question_type = 'ESSAY' THEN 'RUNTIME'
    ELSE 'IMPORTED'
END
WHERE source_type IS DISTINCT FROM CASE
    WHEN question_type = 'ESSAY' THEN 'RUNTIME'
    ELSE 'IMPORTED'
END;

-- ESSAY는 문제만 제공하며 모범답안과 정규화 답안을 저장하지 않는다.
UPDATE problem_answer_unit AS answer_unit
SET answer_raw = NULL,
    answer_normalized = NULL,
    display_unit = NULL,
    compare_method = 'RUBRIC'
FROM problem_question AS question
WHERE question.id = answer_unit.question_id
  AND question.question_type = 'ESSAY'
  AND (answer_unit.answer_raw IS NOT NULL
       OR answer_unit.answer_normalized IS NOT NULL
       OR answer_unit.display_unit IS NOT NULL
       OR answer_unit.compare_method <> 'RUBRIC');

-- ERD의 problem_step.segments 계약에 맞춰 blankId를 unitKey로 변경한다.
UPDATE problem_step AS step
SET segments = (
    SELECT jsonb_agg(
        CASE
            WHEN segment.value ? 'blankId'
                THEN (segment.value - 'blankId')
                     || jsonb_build_object('unitKey', segment.value -> 'blankId')
            ELSE segment.value
        END
        ORDER BY segment.ordinality
    ) AS segments
    FROM jsonb_array_elements(step.segments)
         WITH ORDINALITY AS segment(value, ordinality)
)
WHERE step.segments::text LIKE '%"blankId"%';

-- 정답 문자열에 붙은 단위는 비교값에서 제거하고 display_unit으로 분리한다.
UPDATE problem_answer_unit AS answer_unit
SET display_unit = substring(btrim(answer_unit.answer_raw) FROM '((?:cm|m)\^[23])$'),
    answer_raw = btrim(regexp_replace(answer_unit.answer_raw, '\s*(?:cm|m)\^[23]$', '')),
    answer_normalized = btrim(regexp_replace(answer_unit.answer_normalized, '\s*(?:cm|m)\^[23]$', ''))
FROM problem_question AS question
WHERE question.id = answer_unit.question_id
  AND question.question_type = 'STEP_FILL'
  AND btrim(answer_unit.answer_raw) ~ '(?:cm|m)\^[23]$';

-- LLM 생성 과정에서 프로그램 함수 또는 깨진 Unicode 이스케이프가 들어간 답을 문맥에 맞게 복원한다.
UPDATE problem_answer_unit AS answer_unit
SET answer_raw = correction.answer_raw,
    answer_normalized = correction.answer_normalized,
    compare_method = correction.compare_method
FROM problem_question AS question
JOIN (VALUES
    ('110:11338_11715', 'B1', $text$\frac{3}{2}\pi$text$, $text$3/2*pi$text$, 'SUBST'),
    ('110:11338_11715', 'B2', $text$\frac{7}{2}\pi$text$, $text$7/2*pi$text$, 'SUBST'),
    ('110:11338_11715', 'B3', $text$10\pi$text$, $text$10*pi$text$, 'SUBST'),
    ('110:11380_67006', 'B1', $text$\frac{9}{4}\pi-\frac{9}{2}$text$, $text$9/4*pi-9/2$text$, 'SUBST'),
    ('110:11380_67006', 'B2', $text$\frac{27}{2}-\frac{9}{4}\pi$text$, $text$27/2-9/4*pi$text$, 'SUBST'),
    ('110:13327_26242', 'B1', $text$\frac{4}{3}\pi r^3$text$, $text$4/3*pi*r^3$text$, 'SUBST'),
    ('110:34382_161853', 'B1', $text$a^2-\frac{1}{2}b^2$text$, $text$a^2-1/2*b^2$text$, 'SUBST'),
    ('110:34382_161855', 'B1', $text$x^2-\frac{1}{2}y^2$text$, $text$x^2-1/2*y^2$text$, 'SUBST'),
    ('110:6334_50601', 'B1', $text$5$text$, $text$5$text$, 'VALUE'),
    ('110:6559_1021', 'B2', $text$-a+13\ge1$text$, $text$-a+13>=1$text$, 'SUBST'),
    ('110:6675_58661', 'B1', $text$\frac{6}{5}x$text$, $text$6/5*x$text$, 'SUBST'),
    ('110:6802_2228', 'B1', $text$\frac{28}{25}x$text$, $text$28/25*x$text$, 'SUBST')
) AS correction(source_ref, unit_key, answer_raw, answer_normalized, compare_method)
    ON correction.source_ref = question.source_ref
WHERE question.id = answer_unit.question_id
  AND answer_unit.unit_key = correction.unit_key;

-- 표시용 답에서는 \tfrac를 일반적인 \frac로 통일한다.
UPDATE problem_answer_unit AS answer_unit
SET answer_raw = replace(answer_unit.answer_raw, '\tfrac', '\frac')
FROM problem_question AS question
WHERE question.id = answer_unit.question_id
  AND question.question_type = 'STEP_FILL'
  AND answer_unit.answer_raw LIKE '%\tfrac%';

-- 비교용 답은 LaTeX 명령과 암묵적 곱셈을 단순한 수식 문자열로 정규화한다.
UPDATE problem_answer_unit AS answer_unit
SET answer_normalized = regexp_replace(
    regexp_replace(
        regexp_replace(
            regexp_replace(
                replace(
                    replace(
                        replace(
                            replace(
                                regexp_replace(
                                    replace(answer_unit.answer_normalized, '\tfrac', '\frac'),
                                    '\\frac\{([^{}]+)\}\{([^{}]+)\}',
                                    '\1/\2',
                                    'g'
                                ),
                                '\times', '*'
                            ),
                            '\cdot', '*'
                        ),
                        '\pi', 'pi'
                    ),
                    'π', 'pi'
                ),
                '\s+', '', 'g'
            ),
            '([0-9)])pi', '\1*pi', 'g'
        ),
        'pi([A-Za-z(])', 'pi*\1', 'g'
    ),
    '([0-9)])([A-Za-z(])', '\1*\2', 'g'
)
FROM problem_question AS question
WHERE question.id = answer_unit.question_id
  AND question.question_type = 'STEP_FILL'
  AND answer_unit.answer_normalized IS NOT NULL
  AND (answer_unit.answer_normalized LIKE '%\tfrac%'
       OR answer_unit.answer_normalized LIKE '%\frac%'
       OR answer_unit.answer_normalized LIKE '%\times%'
       OR answer_unit.answer_normalized LIKE '%\cdot%'
       OR answer_unit.answer_normalized LIKE '%\pi%'
       OR answer_unit.answer_normalized LIKE '%π%'
       OR answer_unit.answer_normalized ~ '\s');

-- 보기 파싱이 깨진 문항은 원천 라벨의 번호 순서대로 다시 적재한다.
CREATE TEMPORARY TABLE problem_choice_correction
(
    source_ref    VARCHAR(100) NOT NULL,
    display_order SMALLINT     NOT NULL,
    content       TEXT         NOT NULL,
    PRIMARY KEY (source_ref, display_order)
) ON COMMIT DROP;

INSERT INTO problem_choice_correction (source_ref, display_order, content)
VALUES
    ('30:S3_중등_1_000074', 0, $text$㉠$text$),
    ('30:S3_중등_1_000074', 1, $text$㉡$text$),
    ('30:S3_중등_1_000074', 2, $text$㉢$text$),
    ('30:S3_중등_1_000074', 3, $text$㉣$text$),
    ('30:S3_중등_1_000074', 4, $text$㉤$text$),
    ('30:S3_중등_1_000074', 5, $text$㉥$text$),

    ('30:S3_중등_1_000098', 0, $text$㉠$text$),
    ('30:S3_중등_1_000098', 1, $text$㉡$text$),
    ('30:S3_중등_1_000098', 2, $text$㉢$text$),
    ('30:S3_중등_1_000098', 3, $text$㉣$text$),
    ('30:S3_중등_1_000098', 4, $text$㉤$text$),

    ('30:S3_중등_1_000251', 0, $text$$2$$text$),
    ('30:S3_중등_1_000251', 1, $text$$3$$text$),
    ('30:S3_중등_1_000251', 2, $text$$2 \times 3$$text$),
    ('30:S3_중등_1_000251', 3, $text$$2^{2} \times 3$$text$),
    ('30:S3_중등_1_000251', 4, $text$$2^{2} \times 3^{2}$$text$),

    ('30:S3_중등_1_000309', 0, $text$$\mathrm{A}(-4)$$text$),
    ('30:S3_중등_1_000309', 1, $text$$\mathrm{B}\left(-\frac{9}{4}\right)$$text$),
    ('30:S3_중등_1_000309', 2, $text$$\mathrm{C}\left(-\frac{5}{4}\right)$$text$),
    ('30:S3_중등_1_000309', 3, $text$$\mathrm{D}\left(+\frac{5}{3}\right)$$text$),
    ('30:S3_중등_1_000309', 4, $text$$\mathrm{E}(+3.5)$$text$),

    ('30:S3_중등_1_000328', 0, $text$$a+b=b+a$$text$),
    ('30:S3_중등_1_000328', 1, $text$$a \times b=b \times a$$text$),
    ('30:S3_중등_1_000328', 2, $text$$(a+b)+c=a+(b+c)$$text$),
    ('30:S3_중등_1_000328', 3, $text$$(a \times b) \times c=a \times(b \times c)$$text$),
    ('30:S3_중등_1_000328', 4, $text$$a \times b+a \times c=a \times(b+c)$$text$),

    ('30:S3_중등_1_000369', 0, $text$$(4x-1) \times(-2)$$text$),
    ('30:S3_중등_1_000369', 1, $text$$-4(2x-0.5)$$text$),
    ('30:S3_중등_1_000369', 2, $text$$-2(4x-1)$$text$),
    ('30:S3_중등_1_000369', 3, $text$$2(4x+1)$$text$),
    ('30:S3_중등_1_000369', 4, $text$$(-8x+2) \times(-1)$$text$),

    ('30:S3_중등_1_000407', 0, $text$$-3x=-x+8$$text$),
    ('30:S3_중등_1_000407', 1, $text$$5x-10=2x-1$$text$),
    ('30:S3_중등_1_000407', 2, $text$$2:3=(x+1):2x$$text$),
    ('30:S3_중등_1_000407', 3, $text$$7(x+3)=1-4(x-5)$$text$),
    ('30:S3_중등_1_000407', 4, $text$$4x-(x+21)=3(1-3x)$$text$),

    ('30:S3_중등_1_000431', 0, $text$$\mathrm{A}(3, 2)$$text$),
    ('30:S3_중등_1_000431', 1, $text$$\mathrm{B}(-3, -3)$$text$),
    ('30:S3_중등_1_000431', 2, $text$$\mathrm{C}(-2, -4)$$text$),
    ('30:S3_중등_1_000431', 3, $text$$\mathrm{D}(1, -3)$$text$),
    ('30:S3_중등_1_000431', 4, $text$$\mathrm{E}(4, -1)$$text$),

    ('30:S3_중등_1_000457', 0, $text$①$text$),
    ('30:S3_중등_1_000457', 1, $text$②$text$),
    ('30:S3_중등_1_000457', 2, $text$③$text$),
    ('30:S3_중등_1_000457', 3, $text$④$text$),
    ('30:S3_중등_1_000457', 4, $text$⑤$text$),

    ('30:S3_중등_1_001188', 0, $text$10명$text$),
    ('30:S3_중등_1_001188', 1, $text$11명$text$),
    ('30:S3_중등_1_001188', 2, $text$12명$text$),
    ('30:S3_중등_1_001188', 3, $text$13명$text$),
    ('30:S3_중등_1_001188', 4, $text$14명$text$),

    ('30:S3_중등_1_003559', 0, $text$$\mathrm{A}(1, 3)$$text$),
    ('30:S3_중등_1_003559', 1, $text$$\mathrm{B}(0, -2)$$text$),
    ('30:S3_중등_1_003559', 2, $text$$\mathrm{C}(2, -3)$$text$),
    ('30:S3_중등_1_003559', 3, $text$$\mathrm{D}(-4, 2)$$text$),
    ('30:S3_중등_1_003559', 4, $text$$\mathrm{E}(-3, -4)$$text$),

    ('30:S3_중등_1_011598', 0, $text$시간이 지날수록 물의 양이 증가하다가 일정해지는 그래프$text$),
    ('30:S3_중등_1_011598', 1, $text$물의 양이 증가하다가 잠시 일정해지고 다시 증가하여 일정해지는 그래프$text$),
    ('30:S3_중등_1_011598', 2, $text$물의 양이 증가하다가 감소한 뒤 다시 증가하여 일정해지는 그래프$text$),

    ('30:S3_중등_1_011599', 0, $text$시간이 지날수록 물의 양이 증가하다가 일정해지는 그래프$text$),
    ('30:S3_중등_1_011599', 1, $text$물의 양이 증가하다가 잠시 일정해지고 다시 증가하여 일정해지는 그래프$text$),
    ('30:S3_중등_1_011599', 2, $text$물의 양이 증가하다가 감소한 뒤 다시 증가하여 일정해지는 그래프$text$),

    ('30:S3_중등_1_016605', 0, $text$$(-3) \times(-4)$$text$),
    ('30:S3_중등_1_016605', 1, $text$$(+12) \times\left(-\frac{1}{3}\right)$$text$),
    ('30:S3_중등_1_016605', 2, $text$$(-6) \times(-2)$$text$),
    ('30:S3_중등_1_016605', 3, $text$$(-4) \times(-3)$$text$),
    ('30:S3_중등_1_016605', 4, $text$$(-8) \times(-1.5)$$text$),

    ('30:S3_중등_1_016606', 0, $text$$(-6) \times(-3)$$text$),
    ('30:S3_중등_1_016606', 1, $text$$(+18) \times\left(-\frac{1}{3}\right)$$text$),
    ('30:S3_중등_1_016606', 2, $text$$(-9) \times(-2)$$text$),
    ('30:S3_중등_1_016606', 3, $text$$(-3) \times(-6)$$text$),
    ('30:S3_중등_1_016606', 4, $text$$(-12) \times(-1.5)$$text$),

    ('30:S3_중등_1_016607', 0, $text$$(-7) \times(-3)$$text$),
    ('30:S3_중등_1_016607', 1, $text$$(-6) \times(-3.5)$$text$),
    ('30:S3_중등_1_016607', 2, $text$$(-5) \times(-4.2)$$text$),
    ('30:S3_중등_1_016607', 3, $text$$(-3) \times(-7)$$text$),
    ('30:S3_중등_1_016607', 4, $text$$(+6) \times(-2)$$text$);

DELETE FROM problem_choice AS choice
USING problem_question AS question
WHERE question.id = choice.question_id
  AND question.source_ref IN (
      SELECT DISTINCT source_ref
      FROM problem_choice_correction
  );

INSERT INTO problem_choice (question_id, display_order, content)
SELECT question.id, correction.display_order, correction.content
FROM problem_choice_correction AS correction
JOIN problem_question AS question
  ON question.source_ref = correction.source_ref
ORDER BY question.id, correction.display_order;

-- 객관식 정답은 ERD 기준인 1부터 시작하는 보기 번호로 통일한다.
UPDATE problem_answer_unit AS answer_unit
SET answer_raw = correction.answer_number,
    answer_normalized = correction.answer_number,
    compare_method = 'CHOICE'
FROM problem_question AS question
JOIN (VALUES
    ('30:S3_중등_1_000074', '3'),
    ('30:S3_중등_1_000098', '5'),
    ('30:S3_중등_1_000251', '5'),
    ('30:S3_중등_1_000309', '3'),
    ('30:S3_중등_1_000328', '5'),
    ('30:S3_중등_1_000369', '5'),
    ('30:S3_중등_1_000407', '4'),
    ('30:S3_중등_1_000431', '2'),
    ('30:S3_중등_1_000457', '4'),
    ('30:S3_중등_1_001188', '5'),
    ('30:S3_중등_1_003559', '2'),
    ('30:S3_중등_1_011598', '2'),
    ('30:S3_중등_1_011599', '1'),
    ('30:S3_중등_1_016605', '2'),
    ('30:S3_중등_1_016606', '2'),
    ('30:S3_중등_1_016607', '5')
) AS correction(source_ref, answer_number)
    ON correction.source_ref = question.source_ref
WHERE question.id = answer_unit.question_id
  AND answer_unit.unit_key = 'MAIN';

-- 보기 파싱 중 이미지 설명으로 잘려 나간 발문을 원천 텍스트로 복원한다.
UPDATE problem_question
SET prompt_text = $text$다음 중 네 번째로 계산해야 할 곳을 말하시오.$text$,
    content_blocks = jsonb_set(
        content_blocks,
        '{0,text}',
        to_jsonb($text$다음 중 네 번째로 계산해야 할 곳을 말하시오.$text$::text),
        false
    )
WHERE source_ref = '30:S3_중등_1_000074';

UPDATE problem_question
SET prompt_text = $text$다음은 등식의 성질을 이용하여 방정식 $\frac{1}{5}x-2=-\frac{4}{5}-x$를 푸는 과정이다. ㉠~㉤ 중에서 등식의 성질 $a=b$이면 $\frac{a}{c}=\frac{b}{c}$(단, $c\ne0$)가 사용된 곳을 말하시오.$text$,
    content_blocks = jsonb_set(
        content_blocks,
        '{0,text}',
        to_jsonb($text$다음은 등식의 성질을 이용하여 방정식 $\frac{1}{5}x-2=-\frac{4}{5}-x$를 푸는 과정이다. ㉠~㉤ 중에서 등식의 성질 $a=b$이면 $\frac{a}{c}=\frac{b}{c}$(단, $c\ne0$)가 사용된 곳을 말하시오.$text$::text),
        false
    )
WHERE source_ref = '30:S3_중등_1_000098';

UPDATE problem_question
SET prompt_text = $text$오른쪽 그림과 같은 그래프 ①~⑤에서 정비례 관계 $y=5x$의 그래프를 찾으시오.$text$,
    content_blocks = jsonb_set(
        content_blocks,
        '{0,text}',
        to_jsonb($text$오른쪽 그림과 같은 그래프 ①~⑤에서 정비례 관계 $y=5x$의 그래프를 찾으시오.$text$::text),
        false
    )
WHERE source_ref = '30:S3_중등_1_000457';
