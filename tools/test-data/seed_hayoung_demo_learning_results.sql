-- hayoung@naver.com 계정의 네 학습지를 시연 가능한 제출·채점 상태로 채운다.
--
-- 예외 시나리오
--   - "2026 1학기 종합 평가"의 김민준은 기존 제출과 풀이 시간을 그대로 보존한다.
--   - 김민준은 SUBMITTED / NOT_GRADED 상태를 유지한다.
--   - 같은 평가의 다른 9명은 GRADED지만, 반 전체 채점이 끝나지 않아 공개하지 않는다.
--
-- 완료 시나리오
--   - 나머지 세 학습지는 10명 모두 제출·채점·공개 완료로 만든다.
--   - 학생별 정답률과 문항별 풀이 시간을 다르게 구성한다.
--   - 채점 완료 학생에게 상세 분석 화면용 시연 보고서와 문항별 문장을 만든다.
--
-- 이 파일은 특정 시연 계정의 수동 데이터이므로 Flyway migration이 아니다.

BEGIN;

CREATE TEMP TABLE demo_target_assignment ON COMMIT DROP AS
SELECT wa.id AS assignment_id,
       wa.assigned_at,
       w.id AS worksheet_id,
       w.title,
       w.type AS worksheet_type,
       ROW_NUMBER() OVER (ORDER BY w.id) AS worksheet_number
FROM member_account teacher
JOIN worksheet w
  ON w.owner_teacher_id = teacher.id
 AND w.deleted_at IS NULL
JOIN worksheet_assignment wa
  ON wa.worksheet_id = w.id
WHERE teacher.login_id = 'hayoung@naver.com'
  AND w.title IN (
      '2026 1학기 종합 평가',
      '2026 1학기 종합 평가 - 2',
      '소인수 분해 외 4개 단원 일반 학습',
      '유리수의 대소 관계 외 2개 단원 일반 학습'
  );

DO $$
DECLARE
    target_count INTEGER;
    invalid_assignment_count INTEGER;
    pending_student_count INTEGER;
    pending_answer_count INTEGER;
    pending_time_count INTEGER;
    pending_graded_answer_count INTEGER;
    unsupported_unit_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO target_count
    FROM demo_target_assignment;
    IF target_count <> 4 THEN
        RAISE EXCEPTION '대상 학습지 배정은 정확히 4개여야 합니다. 현재: %', target_count;
    END IF;

    SELECT COUNT(*) INTO invalid_assignment_count
    FROM (
        SELECT target.assignment_id
        FROM demo_target_assignment target
        LEFT JOIN worksheet_assignment_student student
          ON student.assignment_id = target.assignment_id
        GROUP BY target.assignment_id
        HAVING COUNT(student.id) <> 10
    ) invalid_assignment;
    IF invalid_assignment_count <> 0 THEN
        RAISE EXCEPTION '각 대상 학습지에는 학생이 정확히 10명 배정되어 있어야 합니다.';
    END IF;

    SELECT COUNT(*) INTO pending_student_count
    FROM demo_target_assignment target
    JOIN worksheet_assignment_student assignment_student
      ON assignment_student.assignment_id = target.assignment_id
    JOIN member_account student
      ON student.id = assignment_student.student_id
    WHERE target.title = '2026 1학기 종합 평가'
      AND student.name = '김민준'
      AND assignment_student.status = 'SUBMITTED';
    IF pending_student_count <> 1 THEN
        RAISE EXCEPTION '김민준의 종합 평가 제출 대기 행이 정확히 하나여야 합니다.';
    END IF;

    SELECT COUNT(*),
           COUNT(*) FILTER (WHERE answer.grading_status = 'GRADED')
      INTO pending_answer_count, pending_graded_answer_count
    FROM demo_target_assignment target
    JOIN worksheet_assignment_student assignment_student
      ON assignment_student.assignment_id = target.assignment_id
    JOIN member_account student
      ON student.id = assignment_student.student_id
    JOIN submission_answer answer
      ON answer.assignment_student_id = assignment_student.id
    WHERE target.title = '2026 1학기 종합 평가'
      AND student.name = '김민준';
    IF pending_answer_count <> 10 OR pending_graded_answer_count <> 0 THEN
        RAISE EXCEPTION '보존할 김민준 제출은 미채점 답안 10개여야 합니다. 답안: %, 채점: %',
            pending_answer_count, pending_graded_answer_count;
    END IF;

    SELECT COUNT(*) INTO pending_time_count
    FROM demo_target_assignment target
    JOIN worksheet_assignment_student assignment_student
      ON assignment_student.assignment_id = target.assignment_id
    JOIN member_account student
      ON student.id = assignment_student.student_id
    JOIN submission_question_time question_time
      ON question_time.assignment_student_id = assignment_student.id
    WHERE target.title = '2026 1학기 종합 평가'
      AND student.name = '김민준';
    IF pending_time_count <> 10 THEN
        RAISE EXCEPTION '보존할 김민준 제출은 풀이 시간 10개여야 합니다. 현재: %', pending_time_count;
    END IF;

    SELECT COUNT(*) INTO unsupported_unit_count
    FROM demo_target_assignment target
    JOIN worksheet_item item
      ON item.worksheet_id = target.worksheet_id
    JOIN problem_answer_unit answer_unit
      ON answer_unit.question_id = item.question_id
    WHERE answer_unit.compare_method = 'RUBRIC';
    IF unsupported_unit_count <> 0 THEN
        RAISE EXCEPTION '시연 seed에서 임의 채점할 수 없는 RUBRIC 답안 칸이 있습니다. 현재: %',
            unsupported_unit_count;
    END IF;
END
$$;

CREATE TEMP TABLE demo_target_student ON COMMIT DROP AS
SELECT assignment_student.id AS assignment_student_id,
       assignment_student.student_id,
       student.name AS student_name,
       target.assignment_id,
       target.assigned_at,
       target.worksheet_id,
       target.title,
       target.worksheet_type,
       target.worksheet_number,
       ROW_NUMBER() OVER (
           PARTITION BY target.assignment_id
           ORDER BY student.name, student.id
       ) AS student_number,
       CASE student.name
           WHEN '최지우' THEN 100
           WHEN '박서연' THEN 90
           WHEN '강서준' THEN 80
           WHEN '이동규' THEN 80
           WHEN '김민준' THEN 70
           WHEN '한유진' THEN 70
           WHEN '배세빈' THEN 60
           WHEN '정하윤' THEN 60
           WHEN '모수환' THEN 50
           WHEN '이도윤' THEN 40
           ELSE 70
       END AS accuracy_percent,
       target.title = '2026 1학기 종합 평가'
           AND student.name = '김민준' AS preserve_pending,
       target.assigned_at
           + INTERVAL '1 minute'
           + ROW_NUMBER() OVER (
               PARTITION BY target.assignment_id
               ORDER BY student.name, student.id
             ) * INTERVAL '12 seconds' AS demo_submitted_at
FROM demo_target_assignment target
JOIN worksheet_assignment_student assignment_student
  ON assignment_student.assignment_id = target.assignment_id
JOIN member_account student
  ON student.id = assignment_student.student_id;

CREATE TEMP TABLE demo_answer_source ON COMMIT DROP AS
SELECT target.assignment_student_id,
       target.student_id,
       target.student_name,
       target.worksheet_id,
       target.worksheet_type,
       target.student_number,
       target.demo_submitted_at,
       item.id AS worksheet_item_id,
       item.display_order AS item_number,
       item.max_score,
       question.question_type,
       answer_unit.id AS answer_unit_id,
       answer_unit.compare_method,
       answer_unit.answer_raw,
       answer_unit.answer_normalized,
       correct_choice.id AS correct_choice_id,
       wrong_choice.id AS wrong_choice_id,
       (
           MOD(
               item.display_order
                   + target.student_number * 3
                   + target.worksheet_number * 2,
               10
           ) * 10 < target.accuracy_percent
       ) AS is_correct
FROM demo_target_student target
JOIN worksheet_item item
  ON item.worksheet_id = target.worksheet_id
JOIN problem_question question
  ON question.id = item.question_id
JOIN problem_answer_unit answer_unit
  ON answer_unit.question_id = question.id
LEFT JOIN LATERAL (
    SELECT choice.id
    FROM problem_choice choice
    WHERE choice.question_id = question.id
      AND question.question_type = 'MULTIPLE_CHOICE'
      AND choice.display_order = CASE
          WHEN BTRIM(answer_unit.answer_raw) ~ '^[0-9]+$'
          THEN BTRIM(answer_unit.answer_raw)::INTEGER - 1
          ELSE NULL
      END
    LIMIT 1
) correct_choice ON TRUE
LEFT JOIN LATERAL (
    SELECT choice.id
    FROM problem_choice choice
    WHERE choice.question_id = question.id
      AND choice.id <> correct_choice.id
    ORDER BY MOD(choice.display_order + target.student_number + item.display_order, 5),
             choice.display_order
    LIMIT 1
) wrong_choice ON TRUE
WHERE NOT target.preserve_pending;

DO $$
DECLARE
    missing_choice_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO missing_choice_count
    FROM demo_answer_source
    WHERE question_type = 'MULTIPLE_CHOICE'
      AND (correct_choice_id IS NULL OR wrong_choice_id IS NULL);
    IF missing_choice_count <> 0 THEN
        RAISE EXCEPTION '정답 또는 오답 보기를 찾지 못한 객관식 답안 칸이 있습니다. 현재: %',
            missing_choice_count;
    END IF;
END
$$;

-- 김민준의 보존 대상 제출을 제외한 모든 답안 칸을 채우고 채점한다.
INSERT INTO submission_answer (
    assignment_student_id,
    answer_unit_id,
    input_mode,
    selected_choice_id,
    raw_latex,
    normalized,
    answer_image_ref,
    auto_score,
    final_score,
    overridden_by,
    overridden_at,
    compare_method,
    grading_status,
    failure_reason,
    created_at
)
SELECT source.assignment_student_id,
       source.answer_unit_id,
       CASE
           WHEN source.question_type = 'MULTIPLE_CHOICE' THEN 'CHOICE'
           ELSE 'HANDWRITING'
       END,
       CASE
           WHEN source.question_type <> 'MULTIPLE_CHOICE' THEN NULL
           WHEN source.is_correct THEN source.correct_choice_id
           ELSE source.wrong_choice_id
       END,
       CASE
           WHEN source.question_type = 'MULTIPLE_CHOICE' THEN NULL
           WHEN source.is_correct THEN source.answer_raw
           WHEN source.compare_method = 'SET' THEN '항목 1개'
           ELSE (97 + source.student_number + source.item_number)::TEXT
       END,
       CASE
           WHEN source.question_type = 'MULTIPLE_CHOICE' THEN NULL
           WHEN source.is_correct THEN COALESCE(source.answer_normalized, source.answer_raw)
           WHEN source.compare_method = 'SET' THEN '항목1개'
           ELSE (97 + source.student_number + source.item_number)::TEXT
       END,
       NULL,
       CASE
           WHEN source.worksheet_type = 'COMPREHENSIVE_ASSESSMENT' AND source.is_correct
           THEN source.max_score
           WHEN source.worksheet_type = 'COMPREHENSIVE_ASSESSMENT'
           THEN 0
           WHEN source.is_correct THEN 1
           ELSE 0
       END,
       CASE
           WHEN source.worksheet_type = 'COMPREHENSIVE_ASSESSMENT' AND source.is_correct
           THEN source.max_score
           WHEN source.worksheet_type = 'COMPREHENSIVE_ASSESSMENT'
           THEN 0
           WHEN source.is_correct THEN 1
           ELSE 0
       END,
       NULL,
       NULL,
       source.compare_method,
       'GRADED',
       NULL,
       source.demo_submitted_at - INTERVAL '15 seconds'
FROM demo_answer_source source
ON CONFLICT (assignment_student_id, answer_unit_id) DO UPDATE
SET input_mode = EXCLUDED.input_mode,
    selected_choice_id = EXCLUDED.selected_choice_id,
    raw_latex = EXCLUDED.raw_latex,
    normalized = EXCLUDED.normalized,
    answer_image_ref = EXCLUDED.answer_image_ref,
    auto_score = EXCLUDED.auto_score,
    final_score = EXCLUDED.final_score,
    overridden_by = EXCLUDED.overridden_by,
    overridden_at = EXCLUDED.overridden_at,
    compare_method = EXCLUDED.compare_method,
    grading_status = EXCLUDED.grading_status,
    failure_reason = EXCLUDED.failure_reason,
    created_at = EXCLUDED.created_at;

-- 모든 문항을 실제로 열어 본 것처럼 학생·문항별 풀이 시간을 서로 다르게 남긴다.
INSERT INTO submission_question_time (
    assignment_student_id,
    worksheet_item_id,
    time_spent_seconds
)
SELECT DISTINCT source.assignment_student_id,
       source.worksheet_item_id,
       45
           + MOD(
               source.student_number * 17
                   + source.worksheet_id * 3
                   + source.item_number * 11,
               76
             )
           + CASE WHEN source.is_correct THEN 0 ELSE 35 END
FROM demo_answer_source source
ON CONFLICT (assignment_student_id, worksheet_item_id) DO UPDATE
SET time_spent_seconds = EXCLUDED.time_spent_seconds;

-- 답안 수 축은 종합평가=문항, 일반학습=답안 칸이다.
UPDATE worksheet_assignment_student assignment_student
SET status = 'GRADED',
    progress_count = CASE
        WHEN target.worksheet_type = 'COMPREHENSIVE_ASSESSMENT'
        THEN (
            SELECT COUNT(DISTINCT answer_unit.question_id)::SMALLINT
            FROM submission_answer answer
            JOIN problem_answer_unit answer_unit
              ON answer_unit.id = answer.answer_unit_id
            WHERE answer.assignment_student_id = assignment_student.id
        )
        ELSE (
            SELECT COUNT(*)::SMALLINT
            FROM submission_answer answer
            WHERE answer.assignment_student_id = assignment_student.id
        )
    END,
    submitted_at = target.demo_submitted_at,
    graded_at = target.demo_submitted_at + INTERVAL '45 seconds',
    released_at = CASE
        WHEN target.title = '2026 1학기 종합 평가' THEN NULL
        ELSE target.demo_submitted_at + INTERVAL '75 seconds'
    END,
    total_score = CASE
        WHEN target.worksheet_type = 'COMPREHENSIVE_ASSESSMENT'
        THEN (
            SELECT COALESCE(SUM(answer.final_score), 0)
            FROM submission_answer answer
            WHERE answer.assignment_student_id = assignment_student.id
        )
        ELSE NULL
    END
FROM demo_target_student target
WHERE assignment_student.id = target.assignment_student_id
  AND NOT target.preserve_pending;

-- 채점 완료 학생마다 분석 화면에서 즉시 볼 수 있는 시연용 보고서를 만든다.
WITH student_result AS (
    SELECT target.assignment_student_id,
           target.student_name,
           target.title,
           target.demo_submitted_at,
           COUNT(DISTINCT source.worksheet_item_id) AS total_item_count,
           COUNT(DISTINCT source.worksheet_item_id) FILTER (
               WHERE source.is_correct
           ) AS correct_item_count
    FROM demo_target_student target
    JOIN demo_answer_source source
      ON source.assignment_student_id = target.assignment_student_id
    GROUP BY target.assignment_student_id,
             target.student_name,
             target.title,
             target.demo_submitted_at
)
INSERT INTO analysis_report (
    assignment_student_id,
    generation_status,
    summary_message,
    overall_observation,
    prompt_version,
    model_name,
    llm_schema_version,
    last_error_code,
    generated_at,
    created_at,
    updated_at
)
SELECT result.assignment_student_id,
       'READY',
       CASE
           WHEN result.correct_item_count * 100 >= result.total_item_count * 90
           THEN '핵심 개념을 안정적으로 이해하고 있습니다. 응용 문제로 사고의 폭을 넓혀 보세요.'
           WHEN result.correct_item_count * 100 >= result.total_item_count * 70
           THEN '기본 개념은 잘 이해하고 있습니다. 실수한 유형을 다시 풀면 완성도가 높아집니다.'
           WHEN result.correct_item_count * 100 >= result.total_item_count * 50
           THEN '기본 풀이 흐름은 알고 있습니다. 취약한 유형을 단계별로 복습해 보세요.'
           ELSE '핵심 개념을 작은 단계로 나누어 복습하면 풀이의 정확도를 높일 수 있습니다.'
       END,
       result.student_name || ' 학생은 ' ||
       CASE
           WHEN result.correct_item_count * 100 >= result.total_item_count * 90
           THEN '문제의 조건을 정확히 파악하고 계산 과정을 안정적으로 적용했습니다.'
           WHEN result.correct_item_count * 100 >= result.total_item_count * 70
           THEN '전반적인 개념 이해가 좋으며, 일부 계산 실수를 점검하면 더 안정적인 결과가 기대됩니다.'
           WHEN result.correct_item_count * 100 >= result.total_item_count * 50
           THEN '익숙한 유형은 해결했지만 여러 단계가 필요한 문항에서 추가 연습이 필요합니다.'
           ELSE '문제의 조건을 식으로 옮기는 과정과 기본 계산 순서를 중심으로 반복 학습이 필요합니다.'
       END,
       'demo-v1',
       'seeded-demo',
       1,
       NULL,
       result.demo_submitted_at + INTERVAL '65 seconds',
       (result.demo_submitted_at + INTERVAL '65 seconds')::TIMESTAMP,
       (result.demo_submitted_at + INTERVAL '65 seconds')::TIMESTAMP
FROM student_result result
ON CONFLICT (assignment_student_id) DO NOTHING;

-- 채점 결과에 맞춰 문항별 관찰·학습 포인트·재풀이 가이드를 채운다.
WITH item_result AS (
    SELECT source.assignment_student_id,
           source.worksheet_item_id,
           BOOL_AND(source.is_correct) AS is_correct,
           MIN(source.compare_method) AS compare_method,
           MIN(source.demo_submitted_at) AS demo_submitted_at
    FROM demo_answer_source source
    GROUP BY source.assignment_student_id, source.worksheet_item_id
)
INSERT INTO analysis_report_item_message (
    analysis_report_id,
    worksheet_item_id,
    observation,
    learning_point,
    retry_guide,
    created_at,
    updated_at
)
SELECT report.id,
       result.worksheet_item_id,
       CASE
           WHEN result.is_correct
           THEN '문제의 조건을 풀이에 정확히 반영해 올바른 답을 구했습니다.'
           ELSE '조건을 식으로 옮기거나 계산하는 과정에서 한 번 더 확인이 필요합니다.'
       END,
       CASE result.compare_method
           WHEN 'CHOICE' THEN '각 보기와 문제의 조건을 하나씩 대조해 선택하기'
           WHEN 'SET' THEN '요구한 항목을 빠짐없이 정리해 답하기'
           WHEN 'SUBST' THEN '소인수분해식의 밑과 지수를 정확히 표현하기'
           WHEN 'EXACT' THEN '풀이 결과를 문제에서 요구한 형식으로 정확히 쓰기'
           ELSE '계산 순서를 지키고 마지막 값까지 검산하기'
       END,
       CASE
           WHEN result.is_correct
           THEN '같은 개념의 응용 문제를 풀고 풀이 근거를 한 문장으로 설명해 보세요.'
           ELSE '정답을 가린 뒤 문제의 조건에 밑줄을 긋고 한 단계씩 다시 계산해 보세요.'
       END,
       (result.demo_submitted_at + INTERVAL '65 seconds')::TIMESTAMP,
       (result.demo_submitted_at + INTERVAL '65 seconds')::TIMESTAMP
FROM item_result result
JOIN analysis_report report
  ON report.assignment_student_id = result.assignment_student_id
ON CONFLICT (analysis_report_id, worksheet_item_id) DO NOTHING;

DO $$
DECLARE
    invalid_completed_student_count INTEGER;
    invalid_pending_student_count INTEGER;
    unreleased_completed_assignment_count INTEGER;
    report_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_completed_student_count
    FROM demo_target_student target
    JOIN worksheet_assignment_student assignment_student
      ON assignment_student.id = target.assignment_student_id
    WHERE NOT target.preserve_pending
      AND (
          assignment_student.status <> 'GRADED'
          OR assignment_student.submitted_at IS NULL
          OR assignment_student.graded_at IS NULL
          OR EXISTS (
              SELECT 1
              FROM worksheet_item item
              JOIN problem_answer_unit answer_unit
                ON answer_unit.question_id = item.question_id
              LEFT JOIN submission_answer answer
                ON answer.assignment_student_id = assignment_student.id
               AND answer.answer_unit_id = answer_unit.id
              WHERE item.worksheet_id = target.worksheet_id
                AND (answer.id IS NULL OR answer.grading_status <> 'GRADED')
          )
      );
    IF invalid_completed_student_count <> 0 THEN
        RAISE EXCEPTION '채점 완료 조건을 만족하지 못한 학생별 배정이 있습니다. 현재: %',
            invalid_completed_student_count;
    END IF;

    SELECT COUNT(*) INTO invalid_pending_student_count
    FROM demo_target_student target
    JOIN worksheet_assignment_student assignment_student
      ON assignment_student.id = target.assignment_student_id
    JOIN submission_answer answer
      ON answer.assignment_student_id = assignment_student.id
    WHERE target.preserve_pending
      AND (
          assignment_student.status <> 'SUBMITTED'
          OR assignment_student.graded_at IS NOT NULL
          OR assignment_student.released_at IS NOT NULL
          OR answer.grading_status <> 'NOT_GRADED'
      );
    IF invalid_pending_student_count <> 0 THEN
        RAISE EXCEPTION '김민준의 보존 대상 제출 상태가 변경되었습니다.';
    END IF;

    SELECT COUNT(*) INTO unreleased_completed_assignment_count
    FROM demo_target_student target
    JOIN worksheet_assignment_student assignment_student
      ON assignment_student.id = target.assignment_student_id
    WHERE target.title <> '2026 1학기 종합 평가'
      AND assignment_student.released_at IS NULL;
    IF unreleased_completed_assignment_count <> 0 THEN
        RAISE EXCEPTION '완료 대상 학습지에 공개되지 않은 학생 결과가 있습니다. 현재: %',
            unreleased_completed_assignment_count;
    END IF;

    SELECT COUNT(*) INTO report_count
    FROM demo_target_student target
    JOIN analysis_report report
      ON report.assignment_student_id = target.assignment_student_id
    WHERE NOT target.preserve_pending
      AND report.generation_status = 'READY';
    IF report_count <> 39 THEN
        RAISE EXCEPTION '채점 완료 학생의 READY 분석 보고서는 39개여야 합니다. 현재: %', report_count;
    END IF;
END
$$;

COMMIT;
