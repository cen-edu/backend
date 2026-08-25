-- 로컬 데이터 구성과 EC2 운영 환경에서 동일하게 사용할 테스트용 회원 seed.
-- 모든 계정의 초기 비밀번호는 test1234 이며 BCrypt 해시만 저장한다.

WITH teacher_seed(login_id) AS (
    VALUES
        ('test1@naver.com'),
        ('test2@naver.com'),
        ('test3@naver.com'),
        ('test4@naver.com'),
        ('test5@naver.com')
)
INSERT INTO member_account (role, login_id, password_hash, name)
SELECT 'TEACHER',
       login_id,
       '$2a$10$itIsLdXtONPKCE9usG9DCeHMBip0kZqtWHBPbrZZEUTfehDjFyzRa',
       '테스트 교사'
FROM teacher_seed;

WITH teacher_seed(login_id) AS (
    VALUES
        ('test1@naver.com'),
        ('test2@naver.com'),
        ('test3@naver.com'),
        ('test4@naver.com'),
        ('test5@naver.com')
),
student_seed AS (
    SELECT teacher.login_id AS teacher_login_id,
           split_part(teacher.login_id, '@', 1)
               || '_S'
               || lpad(student_no::text, 8, '0') AS student_login_id,
           '테스트 학생 ' || lpad(student_no::text, 2, '0') AS student_name
    FROM teacher_seed teacher
    CROSS JOIN generate_series(1, 10) AS student_no
)
INSERT INTO member_account (role, login_id, password_hash, name)
SELECT 'STUDENT',
       student_login_id,
       '$2a$10$itIsLdXtONPKCE9usG9DCeHMBip0kZqtWHBPbrZZEUTfehDjFyzRa',
       student_name
FROM student_seed;

WITH teacher_seed(login_id) AS (
    VALUES
        ('test1@naver.com'),
        ('test2@naver.com'),
        ('test3@naver.com'),
        ('test4@naver.com'),
        ('test5@naver.com')
),
student_seed AS (
    SELECT teacher.login_id AS teacher_login_id,
           split_part(teacher.login_id, '@', 1)
               || '_S'
               || lpad(student_no::text, 8, '0') AS student_login_id
    FROM teacher_seed teacher
    CROSS JOIN generate_series(1, 10) AS student_no
)
INSERT INTO member_student_profile (
    user_id,
    registration_year,
    grade,
    owner_teacher_id
)
SELECT student.id,
       2026,
       1,
       teacher.id
FROM student_seed seed
JOIN member_account teacher
  ON teacher.login_id = seed.teacher_login_id
 AND teacher.role = 'TEACHER'
JOIN member_account student
  ON student.login_id = seed.student_login_id
 AND student.role = 'STUDENT';
