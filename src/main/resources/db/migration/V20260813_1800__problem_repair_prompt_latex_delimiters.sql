-- 문제 본문에 남은 잘못된 LaTeX 구분자와 잔여 조각을 보정한다.
-- 기존 시드/보정 마이그레이션은 수정하지 않고 후속 마이그레이션으로 처리한다.

-- $\\$는 수식이 아니라 문항 내 줄바꿈 표기로 저장된 값이므로 실제 줄바꿈으로 치환한다.
UPDATE problem_question
SET prompt_text = replace(prompt_text, '$\\$', chr(10))
WHERE position(chr(36) || chr(92) || chr(92) || chr(36) IN prompt_text) > 0;

-- 잘못 남은 배열 종료 문자열을 제거한다.
UPDATE problem_question
SET prompt_text = replace(prompt_text, '}\\end{array}$', '')
WHERE id = 3667
  AND prompt_text LIKE '%}\\end{array}$%';

-- 수식과 단위 사이에 중복된 달러 구분자가 들어간 문항을 보정한다.
UPDATE problem_question
SET prompt_text = replace(prompt_text, '$x$$cm', '$x$ cm')
WHERE id = 2059
  AND prompt_text LIKE '%$x$$cm%';

UPDATE problem_question
SET prompt_text = replace(prompt_text, '$y$$cm', '$y$ cm')
WHERE id = 2059
  AND prompt_text LIKE '%$y$$cm%';

UPDATE problem_question
SET prompt_text = replace(prompt_text, '$175$$cm', '$175$ cm')
WHERE id = 2059
  AND prompt_text LIKE '%$175$$cm%';

UPDATE problem_question
SET prompt_text = replace(prompt_text, '$166$$cm', '$166$ cm')
WHERE id = 2059
  AND prompt_text LIKE '%$166$$cm%';

-- 인라인 수식의 종료 직전에 남은 불필요한 줄바꿈 명령을 제거한다.
UPDATE problem_question
SET prompt_text = replace(prompt_text, '\\\\$', '$')
WHERE id = 3634
  AND prompt_text LIKE '%\\\\$%';
