-- 채점 API(task_06) 성능 측정용 시드.
--
-- 마이그레이션이 아니다. Flyway 이력에 남지 않으며 필요할 때 손으로 돌린다.
-- 지우려면 cleanup.sql 을 돌린다. 삭제 기준은 아래 세 marker 뿐이다.
--
--   반         member_school_class.name = '채점측정 1반'
--   학생       member_account.login_id LIKE 'gradeperf_S%'
--   학습지     worksheet.title LIKE '[채점측정]%'
--
-- 깔리는 것:
--   학생 25명 (로그인 불가 — password_hash 가 bcrypt 형식이 아니다)
--   [채점측정] 종합평가 20문항  → 학생당 20칸 (CHOICE 12 · VALUE/SUBST/SET 각 2 · RUBRIC 2)
--   [채점측정] 일반학습 단계형  → 학생당 ~38칸 (max_score NULL, EXACT 포함)
--   두 학습지 모두 25명 전원 SUBMITTED, 답안은 전부 NOT_GRADED
--
-- 정답률은 약 70%다. random() 을 쓰지 않아 몇 번을 돌려도 같은 답안이 나온다.

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM worksheet WHERE title LIKE '[채점측정]%') THEN
        RAISE EXCEPTION '시드가 이미 깔려 있다. cleanup.sql 을 먼저 돌릴 것';
    END IF;
END $$;

-- 소유 교사. 로그인이 되는 계정이어야 실제 HTTP 호출로 잴 수 있으므로 README 의 signup 으로 먼저
-- 만들어 둔다. 없으면 task_04 가 만든 계정으로 떨어지지만 그 쪽은 비밀번호를 모른다.
CREATE TEMP TABLE seed_teacher ON COMMIT DROP AS
SELECT id FROM member_account
 WHERE login_id = 'gradeperf.teacher@cenedu.test' AND role = 'TEACHER' AND deleted_at IS NULL
 UNION ALL
SELECT id FROM member_account
 WHERE login_id = 'stage2test@cenedu.local' AND role = 'TEACHER' AND deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM member_account
                    WHERE login_id = 'gradeperf.teacher@cenedu.test' AND deleted_at IS NULL);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM seed_teacher) THEN
        RAISE EXCEPTION '측정용 교사가 없다. README 의 signup 을 먼저 실행할 것';
    END IF;
END $$;

-- 1) 반
INSERT INTO member_school_class (academic_year, grade, name, homeroom_teacher_id, display_order)
SELECT 2026, 1, '채점측정 1반', id, 99 FROM seed_teacher;

-- 2) 학생 25명. password_hash 가 bcrypt 형식이 아니라 로그인이 불가능하다.
INSERT INTO member_account (role, login_id, password_hash, name)
SELECT 'STUDENT',
       'gradeperf_S' || lpad(i::text, 2, '0'),
       '!seed-no-login!',
       '측정학생' || lpad(i::text, 2, '0')
FROM generate_series(1, 25) AS i;

INSERT INTO member_student_profile (user_id, registration_year, grade, owner_teacher_id)
SELECT a.id, 2026, 1, t.id
FROM member_account a, seed_teacher t
WHERE a.login_id LIKE 'gradeperf_S%';

INSERT INTO member_class_enrollment (class_id, student_id)
SELECT c.id, a.id
FROM member_school_class c, member_account a
WHERE c.name = '채점측정 1반' AND a.login_id LIKE 'gradeperf_S%';

-- 3) 학습지 2종
INSERT INTO worksheet (title, type, origin, owner_teacher_id, grade, semester, total_score)
SELECT '[채점측정] 종합평가 20문항', 'COMPREHENSIVE_ASSESSMENT', 'STANDARD', id, 1, 'COMMON', 100
FROM seed_teacher
UNION ALL
SELECT '[채점측정] 일반학습 단계형', 'GENERAL_LEARNING', 'STANDARD', id, 1, 'COMMON', NULL
FROM seed_teacher;

-- 4) 종합평가 문항 — 종합평가에 허용된 3유형만 쓴다(AssessmentGenerationService 와 같은 규칙).
--    한 문항의 칸이 전부 같은 비교 방법인 것만 골라 문항↔방법이 1:1로 읽히게 한다.
WITH single_method AS (
    SELECT pq.id, pq.question_type, min(pau.compare_method) AS compare_method
    FROM problem_question pq
    JOIN problem_answer_unit pau ON pau.question_id = pq.id
    WHERE pq.deleted_at IS NULL
    GROUP BY pq.id, pq.question_type
    HAVING count(DISTINCT pau.compare_method) = 1
),
picked AS (
    (SELECT id, 1 AS bucket FROM single_method
      WHERE question_type = 'MULTIPLE_CHOICE' AND compare_method = 'CHOICE' ORDER BY id LIMIT 12)
    UNION ALL
    (SELECT id, 2 FROM single_method
      WHERE question_type = 'SHORT_INPUT' AND compare_method = 'VALUE' ORDER BY id LIMIT 2)
    UNION ALL
    (SELECT id, 3 FROM single_method
      WHERE question_type = 'SHORT_INPUT' AND compare_method = 'SUBST' ORDER BY id LIMIT 2)
    UNION ALL
    (SELECT id, 4 FROM single_method
      WHERE question_type = 'SHORT_INPUT' AND compare_method = 'SET' ORDER BY id LIMIT 2)
    UNION ALL
    (SELECT id, 5 FROM single_method
      WHERE question_type = 'ESSAY' AND compare_method = 'RUBRIC' ORDER BY id LIMIT 2)
)
INSERT INTO worksheet_item (worksheet_id, question_id, display_order, max_score)
SELECT w.id, p.id, row_number() OVER (ORDER BY p.bucket, p.id), 5.00
FROM worksheet w, picked p
WHERE w.title = '[채점측정] 종합평가 20문항';

-- 5) 일반학습 문항 — 단계형만 쓴다. EXACT 칸이 든 문항으로 골라 규칙 채점 5종을 다 덮는다.
--    max_score 는 NULL 이다(일반학습은 배점이 없다).
WITH picked AS (
    SELECT pq.id
    FROM problem_question pq
    WHERE pq.deleted_at IS NULL
      AND pq.question_type = 'STEP_FILL'
      AND EXISTS (SELECT 1 FROM problem_answer_unit x
                   WHERE x.question_id = pq.id AND x.compare_method = 'EXACT')
    ORDER BY pq.id
    LIMIT 12
)
INSERT INTO worksheet_item (worksheet_id, question_id, display_order, max_score)
SELECT w.id, p.id, row_number() OVER (ORDER BY p.id), NULL
FROM worksheet w, picked p
WHERE w.title = '[채점측정] 일반학습 단계형';

-- 6) 배포와 학생별 배정. 전원 제출 완료 상태로 둔다.
INSERT INTO worksheet_assignment (worksheet_id, class_id, assigned_at, due_at)
SELECT w.id, c.id,
       now() - interval '7 days',
       now() - interval '1 day'
FROM worksheet w, member_school_class c
WHERE w.title LIKE '[채점측정]%' AND c.name = '채점측정 1반';

INSERT INTO worksheet_assignment_student
    (assignment_id, student_id, status, progress_count, submitted_at)
SELECT wa.id,
       a.id,
       'SUBMITTED',
       (SELECT count(*) FROM worksheet_item wi WHERE wi.worksheet_id = wa.worksheet_id),
       now() - interval '2 days'
FROM worksheet_assignment wa
JOIN worksheet w ON w.id = wa.worksheet_id
CROSS JOIN member_account a
WHERE w.title LIKE '[채점측정]%' AND a.login_id LIKE 'gradeperf_S%';

-- 7) 답안. 학생×칸마다 한 행이며 전부 NOT_GRADED 다 — 채점은 교사가 트리거한다.
--
--    정답 여부는 (배정 ID, 칸 ID)의 해시로 정한다. random() 이 아니라서 재현된다.
--    normalized 는 채우지 않는다 — 채점 작업 소관이다(SubmissionAnswer 자바독).
INSERT INTO submission_answer
    (assignment_student_id, answer_unit_id, input_mode, selected_choice_id,
     raw_latex, normalized, answer_image_ref, compare_method, grading_status)
SELECT was.id,
       pau.id,
       CASE WHEN pau.compare_method = 'CHOICE' THEN 'CHOICE' ELSE 'HANDWRITING' END,
       CASE WHEN pau.compare_method = 'CHOICE' THEN
                CASE WHEN correct.hit THEN
                    (SELECT pc.id FROM problem_choice pc
                      WHERE pc.question_id = pau.question_id
                        AND pc.display_order + 1 = pau.answer_raw::int)
                ELSE
                    (SELECT pc.id FROM problem_choice pc
                      WHERE pc.question_id = pau.question_id
                        AND pc.display_order + 1 <> pau.answer_raw::int
                      ORDER BY pc.display_order LIMIT 1)
                END
       END,
       CASE
           WHEN pau.compare_method = 'CHOICE' THEN NULL
           WHEN pau.compare_method = 'RUBRIC' THEN '학생이 손으로 쓴 서술형 답안 ' || was.id
           WHEN correct.hit THEN pau.answer_raw
           ELSE pau.answer_raw || '+1'
       END,
       NULL,
       NULL,
       pau.compare_method,
       'NOT_GRADED'
FROM worksheet_assignment_student was
JOIN worksheet_assignment wa ON wa.id = was.assignment_id
JOIN worksheet w ON w.id = wa.worksheet_id
JOIN worksheet_item wi ON wi.worksheet_id = w.id
JOIN problem_answer_unit pau ON pau.question_id = wi.question_id
CROSS JOIN LATERAL (SELECT ((was.id * 7 + pau.id * 3) % 10) < 7 AS hit) AS correct
WHERE w.title LIKE '[채점측정]%';

-- 8) 문항별 소요 시간. 명세 6절 timeSpentSeconds 가 이 표에서 나온다.
INSERT INTO submission_question_time (assignment_student_id, worksheet_item_id, time_spent_seconds)
SELECT was.id, wi.id, 30 + ((was.id * 13 + wi.id * 5) % 240)
FROM worksheet_assignment_student was
JOIN worksheet_assignment wa ON wa.id = was.assignment_id
JOIN worksheet w ON w.id = wa.worksheet_id
JOIN worksheet_item wi ON wi.worksheet_id = w.id
WHERE w.title LIKE '[채점측정]%';

COMMIT;

-- 깔린 결과
SELECT w.title,
       (SELECT count(*) FROM worksheet_item wi WHERE wi.worksheet_id = w.id)   AS 문항,
       count(DISTINCT was.id)                                                  AS 배정,
       count(sa.id)                                                            AS 답안칸,
       count(sa.id) / nullif(count(DISTINCT was.id), 0)                        AS 학생당_칸
FROM worksheet w
JOIN worksheet_assignment wa ON wa.worksheet_id = w.id
JOIN worksheet_assignment_student was ON was.assignment_id = wa.id
LEFT JOIN submission_answer sa ON sa.assignment_student_id = was.id
WHERE w.title LIKE '[채점측정]%'
GROUP BY w.id, w.title
ORDER BY w.title;
