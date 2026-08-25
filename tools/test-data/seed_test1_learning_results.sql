-- test1@naver.com의 현재 세 학습지를 테스트 시나리오 상태로 만든다.
--
-- 완료 상태
--   - 2026 1학기 종합 평가
--   - 소인수 분해 외 4개 단원 일반 학습
-- 미채점 제출 상태
--   - 2026 1학기 종합 평가 - 2
--
-- 이 파일은 수동 생성된 학습지를 원본으로 삼으므로 Flyway migration이 아니다.
-- 데이터가 비어 있는 최초 1회만 실행하며, 조건이 다르면 트랜잭션 시작 단계에서 중단한다.

BEGIN;

CREATE TEMP TABLE seed_target_assignment ON COMMIT DROP AS
SELECT wa.id AS assignment_id,
       w.id AS worksheet_id,
       w.type AS worksheet_type,
       CASE w.title
           WHEN '2026 1학기 종합 평가' THEN 'COMPLETED'
           WHEN '소인수 분해 외 4개 단원 일반 학습' THEN 'COMPLETED'
           WHEN '2026 1학기 종합 평가 - 2' THEN 'PENDING_GRADING'
       END AS scenario
FROM member_account teacher
JOIN worksheet w
  ON w.owner_teacher_id = teacher.id
 AND w.deleted_at IS NULL
JOIN worksheet_assignment wa
  ON wa.worksheet_id = w.id
WHERE teacher.login_id = 'test1@naver.com'
  AND w.title IN (
      '2026 1학기 종합 평가',
      '소인수 분해 외 4개 단원 일반 학습',
      '2026 1학기 종합 평가 - 2'
  );

DO $$
DECLARE
    target_count INTEGER;
    invalid_student_count INTEGER;
    existing_answer_count INTEGER;
    existing_time_count INTEGER;
    invalid_status_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO target_count
    FROM seed_target_assignment;
    IF target_count <> 3 THEN
        RAISE EXCEPTION '대상 학습지 배정은 정확히 3개여야 합니다. 현재: %', target_count;
    END IF;

    SELECT COUNT(*) INTO invalid_student_count
    FROM (
        SELECT target.assignment_id
        FROM seed_target_assignment target
        LEFT JOIN worksheet_assignment_student student
          ON student.assignment_id = target.assignment_id
        GROUP BY target.assignment_id
        HAVING COUNT(student.id) <> 10
    ) invalid_assignment;
    IF invalid_student_count <> 0 THEN
        RAISE EXCEPTION '각 대상 학습지에는 학생이 정확히 10명 배정되어 있어야 합니다.';
    END IF;

    SELECT COUNT(*) INTO existing_answer_count
    FROM submission_answer answer
    JOIN worksheet_assignment_student student
      ON student.id = answer.assignment_student_id
    JOIN seed_target_assignment target
      ON target.assignment_id = student.assignment_id;
    IF existing_answer_count <> 0 THEN
        RAISE EXCEPTION '대상 학습지에 이미 답안 %개가 있어 seed를 중단합니다.', existing_answer_count;
    END IF;

    SELECT COUNT(*) INTO existing_time_count
    FROM submission_question_time question_time
    JOIN worksheet_assignment_student student
      ON student.id = question_time.assignment_student_id
    JOIN seed_target_assignment target
      ON target.assignment_id = student.assignment_id;
    IF existing_time_count <> 0 THEN
        RAISE EXCEPTION '대상 학습지에 이미 풀이 시간 %개가 있어 seed를 중단합니다.', existing_time_count;
    END IF;

    SELECT COUNT(*) INTO invalid_status_count
    FROM worksheet_assignment_student student
    JOIN seed_target_assignment target
      ON target.assignment_id = student.assignment_id
    WHERE student.status <> 'NOT_STARTED'
       OR student.progress_count <> 0;
    IF invalid_status_count <> 0 THEN
        RAISE EXCEPTION '대상 학생별 배정이 시작 전 상태가 아니어서 seed를 중단합니다.';
    END IF;
END
$$;

CREATE TEMP TABLE seed_student_assignment ON COMMIT DROP AS
SELECT student.id AS assignment_student_id,
       student.student_id,
       target.assignment_id,
       target.worksheet_id,
       target.worksheet_type,
       target.scenario,
       ROW_NUMBER() OVER (
           PARTITION BY target.assignment_id
           ORDER BY account.login_id
       ) AS student_number
FROM seed_target_assignment target
JOIN worksheet_assignment_student student
  ON student.assignment_id = target.assignment_id
JOIN member_account account
  ON account.id = student.student_id;

-- 종합평가 두 개의 객관식 답안을 만든다. 학생·문항 조합에 따라 정답과 오답을 섞는다.
WITH assessment_answer AS (
    SELECT student.assignment_student_id,
           student.scenario,
           student.student_number,
           item.display_order AS item_number,
           item.max_score,
           question.id AS question_id,
           answer_unit.id AS answer_unit_id,
           answer_unit.compare_method,
           (MOD((student.student_number + item.display_order)::INTEGER, 4) <> 0) AS is_correct,
           correct_choice.id AS correct_choice_id,
           correct_choice.display_order AS correct_choice_order,
           wrong_choice.id AS wrong_choice_id,
           wrong_choice.display_order AS wrong_choice_order
    FROM seed_student_assignment student
    JOIN worksheet_item item
      ON item.worksheet_id = student.worksheet_id
    JOIN problem_question question
      ON question.id = item.question_id
    JOIN problem_answer_unit answer_unit
      ON answer_unit.question_id = question.id
    JOIN problem_choice correct_choice
      ON correct_choice.question_id = question.id
     AND correct_choice.display_order = answer_unit.answer_normalized::INTEGER - 1
    JOIN LATERAL (
        SELECT choice.id, choice.display_order
        FROM problem_choice choice
        WHERE choice.question_id = question.id
          AND choice.id <> correct_choice.id
        ORDER BY choice.display_order
        LIMIT 1
    ) wrong_choice ON TRUE
    WHERE student.worksheet_type = 'COMPREHENSIVE_ASSESSMENT'
      AND question.question_type = 'MULTIPLE_CHOICE'
)
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
SELECT assignment_student_id,
       answer_unit_id,
       'CHOICE',
       CASE WHEN is_correct THEN correct_choice_id ELSE wrong_choice_id END,
       NULL,
       CASE
           WHEN scenario = 'COMPLETED'
           THEN (CASE WHEN is_correct THEN correct_choice_order ELSE wrong_choice_order END + 1)::TEXT
           ELSE NULL
       END,
       NULL,
       CASE
           WHEN scenario = 'COMPLETED' AND is_correct THEN max_score
           WHEN scenario = 'COMPLETED' THEN 0
           ELSE NULL
       END,
       CASE
           WHEN scenario = 'COMPLETED' AND is_correct THEN max_score
           WHEN scenario = 'COMPLETED' THEN 0
           ELSE NULL
       END,
       NULL,
       NULL,
       compare_method,
       CASE WHEN scenario = 'COMPLETED' THEN 'GRADED' ELSE 'NOT_GRADED' END,
       NULL,
       now() - INTERVAL '2 hours'
FROM assessment_answer;

-- 일반 학습은 모든 답안 칸을 채우고 문항 단위로 정답과 오답을 섞어 채점 완료 상태로 만든다.
WITH learning_answer AS (
    SELECT student.assignment_student_id,
           student.student_number,
           item.display_order AS item_number,
           answer_unit.id AS answer_unit_id,
           answer_unit.answer_raw,
           answer_unit.answer_normalized,
           answer_unit.compare_method,
           (MOD((student.student_number + item.display_order)::INTEGER, 4) <> 0) AS is_correct
    FROM seed_student_assignment student
    JOIN worksheet_item item
      ON item.worksheet_id = student.worksheet_id
    JOIN problem_answer_unit answer_unit
      ON answer_unit.question_id = item.question_id
    WHERE student.worksheet_type = 'GENERAL_LEARNING'
      AND student.scenario = 'COMPLETED'
)
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
SELECT assignment_student_id,
       answer_unit_id,
       'HANDWRITING',
       NULL,
       CASE WHEN is_correct THEN answer_raw ELSE '0' END,
       CASE WHEN is_correct THEN answer_normalized ELSE '0' END,
       NULL,
       CASE WHEN is_correct THEN 1 ELSE 0 END,
       CASE WHEN is_correct THEN 1 ELSE 0 END,
       NULL,
       NULL,
       compare_method,
       'GRADED',
       NULL,
       now() - INTERVAL '2 hours'
FROM learning_answer;

-- 모든 문항을 실제로 열어 본 것처럼 학생별·문항별 풀이 시간을 남긴다.
INSERT INTO submission_question_time (
    assignment_student_id,
    worksheet_item_id,
    time_spent_seconds
)
SELECT student.assignment_student_id,
       item.id,
       (30 + student.student_number * 5 + item.display_order * 7)::INTEGER
FROM seed_student_assignment student
JOIN worksheet_item item
  ON item.worksheet_id = student.worksheet_id;

-- 완료 시나리오는 제출·채점·공개까지, 미채점 시나리오는 제출까지만 반영한다.
UPDATE worksheet_assignment_student student
SET status = CASE
        WHEN target.scenario = 'COMPLETED' THEN 'GRADED'
        ELSE 'SUBMITTED'
    END,
    progress_count = CASE
        WHEN target.worksheet_type = 'GENERAL_LEARNING'
        THEN (
            SELECT COUNT(*)::SMALLINT
            FROM submission_answer answer
            WHERE answer.assignment_student_id = student.id
        )
        ELSE (
            SELECT COUNT(DISTINCT answer_unit.question_id)::SMALLINT
            FROM submission_answer answer
            JOIN problem_answer_unit answer_unit
              ON answer_unit.id = answer.answer_unit_id
            WHERE answer.assignment_student_id = student.id
        )
    END,
    submitted_at = now() - INTERVAL '90 minutes',
    graded_at = CASE
        WHEN target.scenario = 'COMPLETED' THEN now() - INTERVAL '60 minutes'
        ELSE NULL
    END,
    released_at = CASE
        WHEN target.scenario = 'COMPLETED' THEN now() - INTERVAL '30 minutes'
        ELSE NULL
    END,
    total_score = CASE
        WHEN target.scenario = 'COMPLETED'
         AND target.worksheet_type = 'COMPREHENSIVE_ASSESSMENT'
        THEN (
            SELECT COALESCE(SUM(answer.final_score), 0)
            FROM submission_answer answer
            WHERE answer.assignment_student_id = student.id
        )
        ELSE NULL
    END
FROM seed_target_assignment target
WHERE student.assignment_id = target.assignment_id;

COMMIT;
