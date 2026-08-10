-- 기존 analysis baseline에서 생성한 객체를 제거한다.
-- 적용된 마이그레이션은 수정하지 않고 후속 마이그레이션에서 명시적으로 되돌린다.
DROP TABLE IF EXISTS analysis_report;
DROP TABLE IF EXISTS analysis_attempt;
DROP TABLE IF EXISTS analysis_assessment;

-- 교사와 학생의 로그인 계정.
CREATE TABLE member_account
(
    id            BIGSERIAL    PRIMARY KEY,
    role          VARCHAR(10)  NOT NULL,
    login_id      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT uk_member_account_login_id UNIQUE (login_id),
    CONSTRAINT ck_member_account_role CHECK (role IN ('TEACHER', 'STUDENT'))
);

COMMENT ON TABLE member_account IS '교사와 학생의 로그인 계정';
COMMENT ON COLUMN member_account.login_id IS '교사는 이메일, 학생은 서버에서 발급한 아이디를 사용한다';
COMMENT ON COLUMN member_account.deleted_at IS '소프트 삭제 시각. 삭제된 계정의 login_id도 재사용하지 않는다';

-- 학생 계정에만 존재하는 학생 정보.
CREATE TABLE member_student_profile
(
    user_id            BIGINT   PRIMARY KEY,
    registration_year SMALLINT NOT NULL,
    grade              SMALLINT NOT NULL,
    owner_teacher_id   BIGINT   NOT NULL,
    CONSTRAINT fk_member_student_profile_user
        FOREIGN KEY (user_id) REFERENCES member_account (id),
    CONSTRAINT fk_member_student_profile_owner_teacher
        FOREIGN KEY (owner_teacher_id) REFERENCES member_account (id),
    CONSTRAINT ck_member_student_profile_grade CHECK (grade BETWEEN 1 AND 3)
);

CREATE INDEX idx_member_student_profile_owner_teacher
    ON member_student_profile (owner_teacher_id);

COMMENT ON TABLE member_student_profile IS '교사가 생성한 학생의 프로필';
COMMENT ON COLUMN member_student_profile.user_id IS '서비스에서 member_account.role = STUDENT인지 검증한다';
COMMENT ON COLUMN member_student_profile.owner_teacher_id IS '서비스에서 member_account.role = TEACHER인지 검증한다';

-- 학년도 단위의 반. 학기는 저장하지 않는다.
CREATE TABLE member_school_class
(
    id                  BIGSERIAL   PRIMARY KEY,
    academic_year       SMALLINT    NOT NULL,
    grade               SMALLINT    NOT NULL,
    name                VARCHAR(20) NOT NULL,
    homeroom_teacher_id BIGINT      NOT NULL,
    display_order       INTEGER     NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT fk_member_school_class_homeroom_teacher
        FOREIGN KEY (homeroom_teacher_id) REFERENCES member_account (id),
    CONSTRAINT ck_member_school_class_grade CHECK (grade BETWEEN 1 AND 3),
    CONSTRAINT ck_member_school_class_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_member_school_class_homeroom_teacher
    ON member_school_class (homeroom_teacher_id);

COMMENT ON TABLE member_school_class IS '교사가 관리하는 학년도 단위의 반';
COMMENT ON COLUMN member_school_class.homeroom_teacher_id IS '서비스에서 member_account.role = TEACHER인지 검증한다';
COMMENT ON COLUMN member_school_class.display_order IS '교사별 반 정렬 순서. 서비스에서 0부터 연속되도록 관리한다';

-- 학생은 여러 반에 속할 수 있지만 같은 반에는 한 번만 등록할 수 있다.
CREATE TABLE member_class_enrollment
(
    id         BIGSERIAL PRIMARY KEY,
    class_id   BIGINT    NOT NULL,
    student_id BIGINT    NOT NULL,
    CONSTRAINT fk_member_class_enrollment_class
        FOREIGN KEY (class_id) REFERENCES member_school_class (id),
    CONSTRAINT fk_member_class_enrollment_student
        FOREIGN KEY (student_id) REFERENCES member_account (id),
    CONSTRAINT uk_member_class_enrollment_class_student UNIQUE (class_id, student_id)
);

CREATE INDEX idx_member_class_enrollment_student
    ON member_class_enrollment (student_id);

COMMENT ON TABLE member_class_enrollment IS '반과 학생의 다대다 소속 관계';
COMMENT ON COLUMN member_class_enrollment.student_id IS '서비스에서 학생 역할, 소유 교사, 반과 학생의 학년 일치를 검증한다';
