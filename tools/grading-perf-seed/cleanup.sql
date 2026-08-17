-- seed.sql 이 깔아 둔 것을 전부 지운다. marker 밖의 데이터는 건드리지 않는다.

BEGIN;

CREATE TEMP TABLE seed_assignment_student ON COMMIT DROP AS
SELECT was.id
FROM worksheet_assignment_student was
JOIN worksheet_assignment wa ON wa.id = was.assignment_id
JOIN worksheet w ON w.id = wa.worksheet_id
WHERE w.title LIKE '[채점측정]%';

DELETE FROM grading_rubric_result
 WHERE student_answer_id IN (
     SELECT id FROM submission_answer
      WHERE assignment_student_id IN (SELECT id FROM seed_assignment_student));

DELETE FROM submission_answer
 WHERE assignment_student_id IN (SELECT id FROM seed_assignment_student);

DELETE FROM submission_question_time
 WHERE assignment_student_id IN (SELECT id FROM seed_assignment_student);

DELETE FROM worksheet_assignment_student
 WHERE id IN (SELECT id FROM seed_assignment_student);

DELETE FROM worksheet_assignment
 WHERE worksheet_id IN (SELECT id FROM worksheet WHERE title LIKE '[채점측정]%');

DELETE FROM worksheet_item
 WHERE worksheet_id IN (SELECT id FROM worksheet WHERE title LIKE '[채점측정]%');

DELETE FROM worksheet WHERE title LIKE '[채점측정]%';

DELETE FROM member_class_enrollment
 WHERE student_id IN (SELECT id FROM member_account WHERE login_id LIKE 'gradeperf_S%');

DELETE FROM member_student_profile
 WHERE user_id IN (SELECT id FROM member_account WHERE login_id LIKE 'gradeperf_S%');

DELETE FROM member_class_enrollment
 WHERE class_id IN (SELECT id FROM member_school_class WHERE name = '채점측정 1반');

DELETE FROM member_school_class WHERE name = '채점측정 1반';

DELETE FROM member_account WHERE login_id LIKE 'gradeperf_S%';

COMMIT;
