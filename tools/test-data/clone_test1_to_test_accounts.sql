-- test1@naver.com의 완성된 테스트 데이터를 test2~test10에 독립 복제한다.
-- 각 대상 계정은 같은 비밀번호·학생·반·학습지·배포·풀이·채점·분석 결과를 갖되
-- 교사/학생/반/학습지/배정/제출의 내부 ID는 새로 발급받는다.
--
-- 수동 생성 데이터를 원본으로 삼으므로 Flyway migration이 아니다.
-- 대상 계정에 반이나 학습지가 이미 있으면 덮어쓰지 않고 즉시 중단한다.

BEGIN;

CREATE TEMP TABLE clone_target_number (
    account_number INTEGER PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO clone_target_number (account_number)
SELECT generate_series(2, 10);

DO $$
DECLARE
    source_teacher_count INTEGER;
    source_student_count INTEGER;
    source_class_count INTEGER;
    source_worksheet_count INTEGER;
    source_assignment_count INTEGER;
    invalid_teacher_count INTEGER;
    populated_target_count INTEGER;
    invalid_existing_student_count INTEGER;
    source_image_answer_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO source_teacher_count
    FROM member_account
    WHERE login_id = 'test1@naver.com'
      AND role = 'TEACHER'
      AND deleted_at IS NULL;
    IF source_teacher_count <> 1 THEN
        RAISE EXCEPTION '활성 test1 교사 계정이 정확히 하나여야 합니다.';
    END IF;

    SELECT COUNT(*) INTO source_student_count
    FROM member_student_profile profile
    JOIN member_account teacher ON teacher.id = profile.owner_teacher_id
    JOIN member_account student ON student.id = profile.user_id
    WHERE teacher.login_id = 'test1@naver.com'
      AND student.deleted_at IS NULL;
    IF source_student_count <> 10 THEN
        RAISE EXCEPTION 'test1 소유 학생은 정확히 10명이어야 합니다. 현재: %', source_student_count;
    END IF;

    SELECT COUNT(*) INTO source_class_count
    FROM member_school_class class
    JOIN member_account teacher ON teacher.id = class.homeroom_teacher_id
    WHERE teacher.login_id = 'test1@naver.com'
      AND class.deleted_at IS NULL;
    IF source_class_count <> 1 THEN
        RAISE EXCEPTION 'test1 활성 반은 정확히 하나여야 합니다. 현재: %', source_class_count;
    END IF;

    SELECT COUNT(*) INTO source_worksheet_count
    FROM worksheet worksheet
    JOIN member_account teacher ON teacher.id = worksheet.owner_teacher_id
    WHERE teacher.login_id = 'test1@naver.com'
      AND worksheet.deleted_at IS NULL;
    IF source_worksheet_count <> 4 THEN
        RAISE EXCEPTION 'test1 활성 학습지는 정확히 4개여야 합니다. 현재: %', source_worksheet_count;
    END IF;

    SELECT COUNT(*) INTO source_assignment_count
    FROM worksheet_assignment assignment
    JOIN worksheet worksheet ON worksheet.id = assignment.worksheet_id
    JOIN member_account teacher ON teacher.id = worksheet.owner_teacher_id
    WHERE teacher.login_id = 'test1@naver.com'
      AND worksheet.deleted_at IS NULL;
    IF source_assignment_count <> 4 THEN
        RAISE EXCEPTION 'test1 학습지 배정은 정확히 4개여야 합니다. 현재: %', source_assignment_count;
    END IF;

    SELECT COUNT(*) INTO invalid_teacher_count
    FROM clone_target_number target
    JOIN member_account teacher
      ON teacher.login_id = 'test' || target.account_number || '@naver.com'
    WHERE teacher.role <> 'TEACHER'
       OR teacher.deleted_at IS NOT NULL;
    IF invalid_teacher_count <> 0 THEN
        RAISE EXCEPTION '대상 로그인 아이디 중 활성 교사 계정이 아닌 계정이 있습니다.';
    END IF;

    SELECT COUNT(DISTINCT teacher.id) INTO populated_target_count
    FROM clone_target_number target
    JOIN member_account teacher
      ON teacher.login_id = 'test' || target.account_number || '@naver.com'
    LEFT JOIN member_school_class class
      ON class.homeroom_teacher_id = teacher.id
     AND class.deleted_at IS NULL
    LEFT JOIN worksheet worksheet
      ON worksheet.owner_teacher_id = teacher.id
     AND worksheet.deleted_at IS NULL
    WHERE class.id IS NOT NULL
       OR worksheet.id IS NOT NULL;
    IF populated_target_count <> 0 THEN
        RAISE EXCEPTION '대상 계정 중 이미 반이나 학습지가 있는 계정이 있어 복제를 중단합니다.';
    END IF;

    SELECT COUNT(*) INTO invalid_existing_student_count
    FROM (
        SELECT teacher.id
        FROM clone_target_number target
        JOIN member_account teacher
          ON teacher.login_id = 'test' || target.account_number || '@naver.com'
        LEFT JOIN member_student_profile profile
          ON profile.owner_teacher_id = teacher.id
        GROUP BY teacher.id
        HAVING COUNT(profile.user_id) NOT IN (0, 10)
    ) invalid_teacher;
    IF invalid_existing_student_count <> 0 THEN
        RAISE EXCEPTION '대상 계정의 기존 학생 수가 0명 또는 10명이 아니어서 복제를 중단합니다.';
    END IF;

    SELECT COUNT(*) INTO source_image_answer_count
    FROM submission_answer answer
    JOIN worksheet_assignment_student assignment_student
      ON assignment_student.id = answer.assignment_student_id
    JOIN worksheet_assignment assignment
      ON assignment.id = assignment_student.assignment_id
    JOIN worksheet worksheet
      ON worksheet.id = assignment.worksheet_id
    JOIN member_account teacher
      ON teacher.id = worksheet.owner_teacher_id
    WHERE teacher.login_id = 'test1@naver.com'
      AND answer.answer_image_ref IS NOT NULL;
    IF source_image_answer_count <> 0 THEN
        RAISE EXCEPTION '필기 이미지 답안은 스토리지 복사가 필요하므로 복제를 중단합니다. 현재: %',
            source_image_answer_count;
    END IF;
END
$$;

-- 아직 없는 test6~test10 교사 계정을 test1과 동일한 정보로 생성한다.
INSERT INTO member_account (
    role,
    login_id,
    password_hash,
    name,
    created_at,
    updated_at,
    deleted_at
)
SELECT source.role,
       'test' || target.account_number || '@naver.com',
       source.password_hash,
       source.name,
       source.created_at,
       source.updated_at,
       NULL
FROM clone_target_number target
CROSS JOIN member_account source
WHERE source.login_id = 'test1@naver.com'
  AND NOT EXISTS (
      SELECT 1
      FROM member_account existing
      WHERE existing.login_id = 'test' || target.account_number || '@naver.com'
  );

CREATE TEMP TABLE clone_teacher_map ON COMMIT DROP AS
SELECT target.account_number AS target_number,
       source.id AS source_teacher_id,
       destination.id AS target_teacher_id
FROM clone_target_number target
CROSS JOIN member_account source
JOIN member_account destination
  ON destination.login_id = 'test' || target.account_number || '@naver.com'
WHERE source.login_id = 'test1@naver.com';

-- 없는 학생 계정과 프로필을 원본 학생 순번별로 생성한다.
INSERT INTO member_account (
    role,
    login_id,
    password_hash,
    name,
    created_at,
    updated_at,
    deleted_at
)
SELECT source_student.role,
       'test' || teacher_map.target_number || '_S' || right(source_student.login_id, 8),
       source_student.password_hash,
       source_student.name,
       source_student.created_at,
       source_student.updated_at,
       NULL
FROM clone_teacher_map teacher_map
JOIN member_student_profile source_profile
  ON source_profile.owner_teacher_id = teacher_map.source_teacher_id
JOIN member_account source_student
  ON source_student.id = source_profile.user_id
WHERE NOT EXISTS (
    SELECT 1
    FROM member_account existing
    WHERE existing.login_id =
        'test' || teacher_map.target_number || '_S' || right(source_student.login_id, 8)
);

INSERT INTO member_student_profile (
    user_id,
    registration_year,
    grade,
    owner_teacher_id
)
SELECT destination_student.id,
       source_profile.registration_year,
       source_profile.grade,
       teacher_map.target_teacher_id
FROM clone_teacher_map teacher_map
JOIN member_student_profile source_profile
  ON source_profile.owner_teacher_id = teacher_map.source_teacher_id
JOIN member_account source_student
  ON source_student.id = source_profile.user_id
JOIN member_account destination_student
  ON destination_student.login_id =
     'test' || teacher_map.target_number || '_S' || right(source_student.login_id, 8)
LEFT JOIN member_student_profile existing_profile
  ON existing_profile.user_id = destination_student.id
WHERE existing_profile.user_id IS NULL;

CREATE TEMP TABLE clone_student_map ON COMMIT DROP AS
SELECT teacher_map.target_number,
       source_student.id AS source_student_id,
       destination_student.id AS target_student_id
FROM clone_teacher_map teacher_map
JOIN member_student_profile source_profile
  ON source_profile.owner_teacher_id = teacher_map.source_teacher_id
JOIN member_account source_student
  ON source_student.id = source_profile.user_id
JOIN member_account destination_student
  ON destination_student.login_id =
     'test' || teacher_map.target_number || '_S' || right(source_student.login_id, 8)
JOIN member_student_profile destination_profile
  ON destination_profile.user_id = destination_student.id
 AND destination_profile.owner_teacher_id = teacher_map.target_teacher_id;

DO $$
DECLARE
    student_map_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO student_map_count FROM clone_student_map;
    IF student_map_count <> 90 THEN
        RAISE EXCEPTION '학생 매핑은 90개여야 합니다. 현재: %', student_map_count;
    END IF;
END
$$;

CREATE TEMP TABLE clone_class_map (
    target_number INTEGER NOT NULL,
    source_class_id BIGINT NOT NULL,
    target_class_id BIGINT NOT NULL,
    PRIMARY KEY (target_number, source_class_id)
) ON COMMIT DROP;

DO $$
DECLARE
    source_row RECORD;
    created_class_id BIGINT;
BEGIN
    FOR source_row IN
        SELECT teacher_map.target_number,
               source_class.*
        FROM clone_teacher_map teacher_map
        JOIN member_school_class source_class
          ON source_class.homeroom_teacher_id = teacher_map.source_teacher_id
         AND source_class.deleted_at IS NULL
        ORDER BY teacher_map.target_number, source_class.id
    LOOP
        INSERT INTO member_school_class (
            academic_year,
            grade,
            name,
            homeroom_teacher_id,
            display_order,
            created_at,
            updated_at,
            deleted_at
        )
        SELECT source_row.academic_year,
               source_row.grade,
               source_row.name,
               teacher_map.target_teacher_id,
               source_row.display_order,
               source_row.created_at,
               source_row.updated_at,
               NULL
        FROM clone_teacher_map teacher_map
        WHERE teacher_map.target_number = source_row.target_number
        RETURNING id INTO created_class_id;

        INSERT INTO clone_class_map (target_number, source_class_id, target_class_id)
        VALUES (source_row.target_number, source_row.id, created_class_id);
    END LOOP;
END
$$;

INSERT INTO member_class_enrollment (class_id, student_id)
SELECT class_map.target_class_id,
       student_map.target_student_id
FROM member_class_enrollment source_enrollment
JOIN clone_class_map class_map
  ON class_map.source_class_id = source_enrollment.class_id
JOIN clone_student_map student_map
  ON student_map.target_number = class_map.target_number
 AND student_map.source_student_id = source_enrollment.student_id;

CREATE TEMP TABLE clone_worksheet_map (
    target_number INTEGER NOT NULL,
    source_worksheet_id BIGINT NOT NULL,
    target_worksheet_id BIGINT NOT NULL,
    PRIMARY KEY (target_number, source_worksheet_id)
) ON COMMIT DROP;

DO $$
DECLARE
    source_row RECORD;
    created_worksheet_id BIGINT;
BEGIN
    FOR source_row IN
        SELECT teacher_map.target_number,
               source_worksheet.*
        FROM clone_teacher_map teacher_map
        JOIN worksheet source_worksheet
          ON source_worksheet.owner_teacher_id = teacher_map.source_teacher_id
         AND source_worksheet.deleted_at IS NULL
        ORDER BY teacher_map.target_number, source_worksheet.id
    LOOP
        INSERT INTO worksheet (
            title,
            type,
            origin,
            owner_teacher_id,
            grade,
            semester,
            total_score,
            source_assignment_id,
            parent_worksheet_id,
            created_at,
            deleted_at
        )
        SELECT source_row.title,
               source_row.type,
               source_row.origin,
               teacher_map.target_teacher_id,
               source_row.grade,
               source_row.semester,
               source_row.total_score,
               NULL,
               NULL,
               source_row.created_at,
               NULL
        FROM clone_teacher_map teacher_map
        WHERE teacher_map.target_number = source_row.target_number
        RETURNING id INTO created_worksheet_id;

        INSERT INTO clone_worksheet_map (
            target_number,
            source_worksheet_id,
            target_worksheet_id
        )
        VALUES (source_row.target_number, source_row.id, created_worksheet_id);
    END LOOP;
END
$$;

INSERT INTO worksheet_gen_spec (
    worksheet_id,
    sub_unit_id,
    difficulty,
    question_count,
    question_type
)
SELECT worksheet_map.target_worksheet_id,
       source_spec.sub_unit_id,
       source_spec.difficulty,
       source_spec.question_count,
       source_spec.question_type
FROM worksheet_gen_spec source_spec
JOIN clone_worksheet_map worksheet_map
  ON worksheet_map.source_worksheet_id = source_spec.worksheet_id;

CREATE TEMP TABLE clone_worksheet_item_map (
    target_number INTEGER NOT NULL,
    source_item_id BIGINT NOT NULL,
    target_item_id BIGINT NOT NULL,
    PRIMARY KEY (target_number, source_item_id)
) ON COMMIT DROP;

DO $$
DECLARE
    source_row RECORD;
    created_item_id BIGINT;
BEGIN
    FOR source_row IN
        SELECT worksheet_map.target_number,
               source_item.*,
               worksheet_map.target_worksheet_id
        FROM clone_worksheet_map worksheet_map
        JOIN worksheet_item source_item
          ON source_item.worksheet_id = worksheet_map.source_worksheet_id
        ORDER BY worksheet_map.target_number, source_item.id
    LOOP
        INSERT INTO worksheet_item (
            worksheet_id,
            question_id,
            display_order,
            max_score,
            custom_stage,
            support_mode
        )
        VALUES (
            source_row.target_worksheet_id,
            source_row.question_id,
            source_row.display_order,
            source_row.max_score,
            source_row.custom_stage,
            source_row.support_mode
        )
        RETURNING id INTO created_item_id;

        INSERT INTO clone_worksheet_item_map (
            target_number,
            source_item_id,
            target_item_id
        )
        VALUES (source_row.target_number, source_row.id, created_item_id);
    END LOOP;
END
$$;

CREATE TEMP TABLE clone_assignment_map (
    target_number INTEGER NOT NULL,
    source_assignment_id BIGINT NOT NULL,
    target_assignment_id BIGINT NOT NULL,
    PRIMARY KEY (target_number, source_assignment_id)
) ON COMMIT DROP;

DO $$
DECLARE
    source_row RECORD;
    created_assignment_id BIGINT;
BEGIN
    FOR source_row IN
        SELECT worksheet_map.target_number,
               source_assignment.*,
               worksheet_map.target_worksheet_id,
               class_map.target_class_id,
               student_map.target_student_id
        FROM clone_worksheet_map worksheet_map
        JOIN worksheet_assignment source_assignment
          ON source_assignment.worksheet_id = worksheet_map.source_worksheet_id
        LEFT JOIN clone_class_map class_map
          ON class_map.target_number = worksheet_map.target_number
         AND class_map.source_class_id = source_assignment.class_id
        LEFT JOIN clone_student_map student_map
          ON student_map.target_number = worksheet_map.target_number
         AND student_map.source_student_id = source_assignment.student_id
        ORDER BY worksheet_map.target_number, source_assignment.id
    LOOP
        INSERT INTO worksheet_assignment (
            worksheet_id,
            class_id,
            student_id,
            assigned_at,
            due_at
        )
        VALUES (
            source_row.target_worksheet_id,
            source_row.target_class_id,
            source_row.target_student_id,
            source_row.assigned_at,
            source_row.due_at
        )
        RETURNING id INTO created_assignment_id;

        INSERT INTO clone_assignment_map (
            target_number,
            source_assignment_id,
            target_assignment_id
        )
        VALUES (source_row.target_number, source_row.id, created_assignment_id);
    END LOOP;
END
$$;

-- 맞춤 학습지가 포함된 경우 원본 계보와 출처 배정도 대상 ID로 다시 연결한다.
UPDATE worksheet destination
SET parent_worksheet_id = parent_map.target_worksheet_id
FROM clone_worksheet_map worksheet_map
JOIN worksheet source
  ON source.id = worksheet_map.source_worksheet_id
JOIN clone_worksheet_map parent_map
  ON parent_map.target_number = worksheet_map.target_number
 AND parent_map.source_worksheet_id = source.parent_worksheet_id
WHERE destination.id = worksheet_map.target_worksheet_id;

UPDATE worksheet destination
SET source_assignment_id = assignment_map.target_assignment_id
FROM clone_worksheet_map worksheet_map
JOIN worksheet source
  ON source.id = worksheet_map.source_worksheet_id
JOIN clone_assignment_map assignment_map
  ON assignment_map.target_number = worksheet_map.target_number
 AND assignment_map.source_assignment_id = source.source_assignment_id
WHERE destination.id = worksheet_map.target_worksheet_id;

CREATE TEMP TABLE clone_assignment_student_map (
    target_number INTEGER NOT NULL,
    source_assignment_student_id BIGINT NOT NULL,
    target_assignment_student_id BIGINT NOT NULL,
    PRIMARY KEY (target_number, source_assignment_student_id)
) ON COMMIT DROP;

DO $$
DECLARE
    source_row RECORD;
    created_assignment_student_id BIGINT;
BEGIN
    FOR source_row IN
        SELECT assignment_map.target_number,
               source_student.*,
               assignment_map.target_assignment_id,
               student_map.target_student_id
        FROM clone_assignment_map assignment_map
        JOIN worksheet_assignment_student source_student
          ON source_student.assignment_id = assignment_map.source_assignment_id
        JOIN clone_student_map student_map
          ON student_map.target_number = assignment_map.target_number
         AND student_map.source_student_id = source_student.student_id
        ORDER BY assignment_map.target_number, source_student.id
    LOOP
        INSERT INTO worksheet_assignment_student (
            assignment_id,
            student_id,
            status,
            progress_count,
            submitted_at,
            graded_at,
            released_at,
            total_score
        )
        VALUES (
            source_row.target_assignment_id,
            source_row.target_student_id,
            source_row.status,
            source_row.progress_count,
            source_row.submitted_at,
            source_row.graded_at,
            source_row.released_at,
            source_row.total_score
        )
        RETURNING id INTO created_assignment_student_id;

        INSERT INTO clone_assignment_student_map (
            target_number,
            source_assignment_student_id,
            target_assignment_student_id
        )
        VALUES (
            source_row.target_number,
            source_row.id,
            created_assignment_student_id
        );
    END LOOP;
END
$$;

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
SELECT assignment_student_map.target_assignment_student_id,
       source_answer.answer_unit_id,
       source_answer.input_mode,
       source_answer.selected_choice_id,
       source_answer.raw_latex,
       source_answer.normalized,
       NULL,
       source_answer.auto_score,
       source_answer.final_score,
       CASE
           WHEN source_answer.overridden_by = teacher_map.source_teacher_id
           THEN teacher_map.target_teacher_id
           ELSE source_answer.overridden_by
       END,
       source_answer.overridden_at,
       source_answer.compare_method,
       source_answer.grading_status,
       source_answer.failure_reason,
       source_answer.created_at
FROM clone_assignment_student_map assignment_student_map
JOIN submission_answer source_answer
  ON source_answer.assignment_student_id =
     assignment_student_map.source_assignment_student_id
JOIN clone_teacher_map teacher_map
  ON teacher_map.target_number = assignment_student_map.target_number;

CREATE TEMP TABLE clone_answer_map ON COMMIT DROP AS
SELECT assignment_student_map.target_number,
       source_answer.id AS source_answer_id,
       destination_answer.id AS target_answer_id
FROM clone_assignment_student_map assignment_student_map
JOIN submission_answer source_answer
  ON source_answer.assignment_student_id =
     assignment_student_map.source_assignment_student_id
JOIN submission_answer destination_answer
  ON destination_answer.assignment_student_id =
     assignment_student_map.target_assignment_student_id
 AND destination_answer.answer_unit_id = source_answer.answer_unit_id;

INSERT INTO submission_question_time (
    assignment_student_id,
    worksheet_item_id,
    time_spent_seconds
)
SELECT assignment_student_map.target_assignment_student_id,
       item_map.target_item_id,
       source_time.time_spent_seconds
FROM clone_assignment_student_map assignment_student_map
JOIN submission_question_time source_time
  ON source_time.assignment_student_id =
     assignment_student_map.source_assignment_student_id
JOIN clone_worksheet_item_map item_map
  ON item_map.target_number = assignment_student_map.target_number
 AND item_map.source_item_id = source_time.worksheet_item_id;

INSERT INTO grading_rubric_result (
    student_answer_id,
    rubric_item_id,
    satisfied,
    evidence
)
SELECT answer_map.target_answer_id,
       source_result.rubric_item_id,
       source_result.satisfied,
       source_result.evidence
FROM clone_answer_map answer_map
JOIN grading_rubric_result source_result
  ON source_result.student_answer_id = answer_map.source_answer_id;

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
SELECT assignment_student_map.target_assignment_student_id,
       source_report.generation_status,
       source_report.summary_message,
       source_report.overall_observation,
       source_report.prompt_version,
       source_report.model_name,
       source_report.llm_schema_version,
       source_report.last_error_code,
       source_report.generated_at,
       source_report.created_at,
       source_report.updated_at
FROM clone_assignment_student_map assignment_student_map
JOIN analysis_report source_report
  ON source_report.assignment_student_id =
     assignment_student_map.source_assignment_student_id;

CREATE TEMP TABLE clone_analysis_report_map ON COMMIT DROP AS
SELECT assignment_student_map.target_number,
       source_report.id AS source_report_id,
       destination_report.id AS target_report_id
FROM clone_assignment_student_map assignment_student_map
JOIN analysis_report source_report
  ON source_report.assignment_student_id =
     assignment_student_map.source_assignment_student_id
JOIN analysis_report destination_report
  ON destination_report.assignment_student_id =
     assignment_student_map.target_assignment_student_id;

INSERT INTO analysis_report_item_message (
    analysis_report_id,
    worksheet_item_id,
    observation,
    learning_point,
    retry_guide,
    created_at,
    updated_at
)
SELECT report_map.target_report_id,
       item_map.target_item_id,
       source_message.observation,
       source_message.learning_point,
       source_message.retry_guide,
       source_message.created_at,
       source_message.updated_at
FROM clone_analysis_report_map report_map
JOIN analysis_report_item_message source_message
  ON source_message.analysis_report_id = report_map.source_report_id
JOIN clone_worksheet_item_map item_map
  ON item_map.target_number = report_map.target_number
 AND item_map.source_item_id = source_message.worksheet_item_id;

DO $$
DECLARE
    invalid_target_count INTEGER;
    source_answer_count INTEGER;
    target_answer_count INTEGER;
    source_time_count INTEGER;
    target_time_count INTEGER;
    source_report_count INTEGER;
    target_report_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_target_count
    FROM (
        SELECT teacher_map.target_number
        FROM clone_teacher_map teacher_map
        LEFT JOIN member_student_profile profile
          ON profile.owner_teacher_id = teacher_map.target_teacher_id
        LEFT JOIN member_school_class class
          ON class.homeroom_teacher_id = teacher_map.target_teacher_id
         AND class.deleted_at IS NULL
        LEFT JOIN worksheet worksheet
          ON worksheet.owner_teacher_id = teacher_map.target_teacher_id
         AND worksheet.deleted_at IS NULL
        GROUP BY teacher_map.target_number
        HAVING COUNT(DISTINCT profile.user_id) <> 10
            OR COUNT(DISTINCT class.id) <> 1
            OR COUNT(DISTINCT worksheet.id) <> 4
    ) invalid_target;
    IF invalid_target_count <> 0 THEN
        RAISE EXCEPTION '복제 결과의 학생·반·학습지 수가 원본과 다른 대상 계정이 있습니다.';
    END IF;

    SELECT COUNT(*) INTO source_answer_count
    FROM submission_answer answer
    JOIN worksheet_assignment_student assignment_student
      ON assignment_student.id = answer.assignment_student_id
    JOIN worksheet_assignment assignment
      ON assignment.id = assignment_student.assignment_id
    JOIN worksheet worksheet
      ON worksheet.id = assignment.worksheet_id
    WHERE worksheet.owner_teacher_id = (
        SELECT id FROM member_account WHERE login_id = 'test1@naver.com'
    );

    SELECT COUNT(*) INTO target_answer_count
    FROM clone_answer_map;
    IF target_answer_count <> source_answer_count * 9 THEN
        RAISE EXCEPTION '답안 복제 수가 예상과 다릅니다. 원본 %, 대상 %',
            source_answer_count, target_answer_count;
    END IF;

    SELECT COUNT(*) INTO source_time_count
    FROM submission_question_time question_time
    JOIN worksheet_assignment_student assignment_student
      ON assignment_student.id = question_time.assignment_student_id
    JOIN worksheet_assignment assignment
      ON assignment.id = assignment_student.assignment_id
    JOIN worksheet worksheet
      ON worksheet.id = assignment.worksheet_id
    WHERE worksheet.owner_teacher_id = (
        SELECT id FROM member_account WHERE login_id = 'test1@naver.com'
    );

    SELECT COUNT(*) INTO target_time_count
    FROM submission_question_time question_time
    JOIN clone_assignment_student_map assignment_student_map
      ON assignment_student_map.target_assignment_student_id =
         question_time.assignment_student_id;
    IF target_time_count <> source_time_count * 9 THEN
        RAISE EXCEPTION '풀이 시간 복제 수가 예상과 다릅니다. 원본 %, 대상 %',
            source_time_count, target_time_count;
    END IF;

    SELECT COUNT(*) INTO source_report_count
    FROM analysis_report report
    JOIN worksheet_assignment_student assignment_student
      ON assignment_student.id = report.assignment_student_id
    JOIN worksheet_assignment assignment
      ON assignment.id = assignment_student.assignment_id
    JOIN worksheet worksheet
      ON worksheet.id = assignment.worksheet_id
    WHERE worksheet.owner_teacher_id = (
        SELECT id FROM member_account WHERE login_id = 'test1@naver.com'
    );

    SELECT COUNT(*) INTO target_report_count
    FROM clone_analysis_report_map;
    IF target_report_count <> source_report_count * 9 THEN
        RAISE EXCEPTION '분석 보고서 복제 수가 예상과 다릅니다. 원본 %, 대상 %',
            source_report_count, target_report_count;
    END IF;
END
$$;

COMMIT;
