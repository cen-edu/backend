-- 정답 앞에 붙은 문항 번호와 보기 구분 기호를 채점값에서 제거한다.
-- answer_raw는 원문 보존을 위해 수정하지 않는다.

-- 비객관식 정답의 문항 번호(①~⑨)는 채점값이 아니므로 제거한다.
UPDATE problem_answer_unit AS answer_unit
SET answer_normalized = btrim(
    regexp_replace(
        answer_unit.answer_normalized,
        '^[[:space:]]*[①-⑨][[:space:]]*',
        '',
        'g'
    )
)
FROM problem_question AS question
WHERE question.id = answer_unit.question_id
  AND question.question_type <> 'MULTIPLE_CHOICE'
  AND answer_unit.answer_normalized ~ '^[[:space:]]*[①-⑨][[:space:]]*';

-- -㉠~-㉭에서 앞의 대시는 음수 기호가 아니라 보기 구분 기호이므로 제거한다.
UPDATE problem_answer_unit
SET answer_normalized = regexp_replace(
    answer_normalized,
    '^[[:space:]]*-[[:space:]]*([㉠-㉭])',
    chr(92) || '1',
    'g'
)
WHERE answer_normalized ~ '^[[:space:]]*-[[:space:]]*[㉠-㉭]';
