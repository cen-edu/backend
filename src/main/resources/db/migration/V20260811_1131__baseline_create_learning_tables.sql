-- learning 영역 baseline.
--
-- 노션 "ERD" 페이지(Final Project 하위)의 B~E 도메인 16테이블을 backend 규칙
-- (도메인 접두어, AGENTS.md 6절)으로 옮긴 것이다. A 도메인 4테이블은
-- V20260810_1023__member_create_tables_and_drop_analysis.sql 로 이미 적재되어 있다.
--
-- 논리명(노션 ERD) ↔ 물리명 매핑 — 이 주석이 매핑의 정본이다.
--   curriculum_unit        → curriculum_unit
--   question               → problem_question
--   question_step          → problem_step
--   question_answer_unit   → problem_answer_unit
--   question_choice        → problem_choice
--   question_asset         → problem_asset
--   question_rubric_item   → problem_rubric_item
--   question_topic         → problem_topic
--   worksheet              → worksheet
--   worksheet_gen_spec     → worksheet_gen_spec
--   worksheet_item         → worksheet_item
--   assignment             → worksheet_assignment
--   assignment_student     → worksheet_assignment_student
--   student_answer         → submission_answer
--   student_question_time  → submission_question_time
--   student_answer_rubric  → grading_rubric_result
--   (app_user/student_profile/school_class/class_member
--     → member_account/member_student_profile/member_school_class/member_class_enrollment, 기적재)
--
-- 식별자 정책: PK는 전 테이블 bigserial. 유일한 예외는 problem_topic.code(varchar(50)) —
-- 원천 데이터의 questionTopic 값을 자연키로 그대로 쓴다.
--
-- 시각 타입: timestamptz. [SURVEY-B] BaseTimeEntity 실물 확인 결과에 따라
-- created_at/updated_at 운용 방식이 정해지며, 스키마 자체는 노션 ERD를 따라
-- updated_at 없는 테이블은 만들지 않았다.
--
-- 정답 위치 정책: 정답은 problem_answer_unit 에만 있다. problem_choice 에는
-- 정답 표시 컬럼이 없다 (학생 응답에서 정답 누출을 구조적으로 차단).
--
-- [미결] question_type 의 SHORT_INPUT 은 제외 확정 이력과 페이지 잔존이 충돌하는
-- 미결 항목이다. CHECK 에는 포함해 두었다 — 값을 좁히는 것은 후속 마이그레이션으로
-- 가능하지만 넓히는 것은 데이터 적재 후 재작업이 되기 때문이다.

-- ============================================================
-- B. 교육과정
-- ============================================================

-- 대단원/중단원/소단원 자기참조 트리. 초기 적재 1회로 끝나는 고정 참조 데이터라
-- 타임스탬프·소프트 삭제 컬럼이 없다.
CREATE TABLE curriculum_unit
(
    id            BIGSERIAL    PRIMARY KEY,
    parent_id     BIGINT,
    external_key  VARCHAR(255) NOT NULL,
    unit_level    VARCHAR(12)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    grade         SMALLINT     NOT NULL,
    semester      SMALLINT,
    display_order INTEGER      NOT NULL,
    CONSTRAINT fk_curriculum_unit_parent
        FOREIGN KEY (parent_id) REFERENCES curriculum_unit (id),
    CONSTRAINT uk_curriculum_unit_external_key UNIQUE (external_key),
    CONSTRAINT ck_curriculum_unit_level
        CHECK (unit_level IN ('MAJOR_UNIT', 'MIDDLE_UNIT', 'SUB_UNIT')),
    CONSTRAINT ck_curriculum_unit_grade CHECK (grade BETWEEN 1 AND 3),
    CONSTRAINT ck_curriculum_unit_semester CHECK (semester IN (1, 2)),
    CONSTRAINT ck_curriculum_unit_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_curriculum_unit_parent ON curriculum_unit (parent_id);

COMMENT ON TABLE curriculum_unit IS '교육과정 단원 트리. 조회 전용, 초기 시드 26행';
COMMENT ON COLUMN curriculum_unit.external_key IS 'EBS 원천 단원 키. 전처리 산출물과 대조하는 유일한 축';
COMMENT ON COLUMN curriculum_unit.name IS 'external_key에서 분해한 실측 표기 그대로 사용한다. 임의 수정 시 문항 매핑 조인이 깨진다';
COMMENT ON COLUMN curriculum_unit.semester IS '대단원(MAJOR_UNIT)에만 값이 있다. 하위 단원은 NULL이며 상위를 따른다';
COMMENT ON COLUMN curriculum_unit.parent_id IS '계층 순서(대>중>소) 검증은 서비스가 한다. FK만으로는 소단원이 대단원을 직접 가리키는 것을 막지 못한다';

-- ============================================================
-- C. 문제은행 (problem 도메인)
-- ============================================================

-- 원천 데이터의 주제(questionTopic) 마스터. 소단원과 함수 관계(위반 0종 실측)다.
CREATE TABLE problem_topic
(
    code        VARCHAR(50)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    sub_unit_id BIGINT       NOT NULL,
    CONSTRAINT fk_problem_topic_sub_unit
        FOREIGN KEY (sub_unit_id) REFERENCES curriculum_unit (id)
);

CREATE INDEX idx_problem_topic_sub_unit ON problem_topic (sub_unit_id);

COMMENT ON TABLE problem_topic IS '문항 주제 마스터(235행). 문제 추천의 1차 축';
COMMENT ON COLUMN problem_topic.code IS '자연키. bigserial 원칙의 유일한 의도적 예외';
COMMENT ON COLUMN problem_topic.sub_unit_id IS '소단원 레벨인지 서비스가 검증한다';

-- 모든 유형의 문항이 이 한 테이블에 들어간다.
CREATE TABLE problem_question
(
    id                       BIGSERIAL    PRIMARY KEY,
    source_type              VARCHAR(20)  NOT NULL,
    source_ref               VARCHAR(255),
    source_dataset_code      VARCHAR(30),
    derived_from_question_id BIGINT,
    sub_unit_id              BIGINT       NOT NULL,
    topic_code               VARCHAR(50),
    difficulty               SMALLINT     NOT NULL,
    question_type            VARCHAR(20)  NOT NULL,
    presentation             VARCHAR(20)  NOT NULL,
    content_blocks           JSONB        NOT NULL,
    prompt_text              TEXT         NOT NULL,
    explanation              TEXT,
    learning_guide           JSONB,
    hint_text                TEXT,
    verification_status      VARCHAR(20),
    verification_attempts    SMALLINT     NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at               TIMESTAMPTZ,
    CONSTRAINT fk_problem_question_derived_from
        FOREIGN KEY (derived_from_question_id) REFERENCES problem_question (id),
    CONSTRAINT fk_problem_question_sub_unit
        FOREIGN KEY (sub_unit_id) REFERENCES curriculum_unit (id),
    CONSTRAINT fk_problem_question_topic
        FOREIGN KEY (topic_code) REFERENCES problem_topic (code),
    CONSTRAINT uk_problem_question_source_ref UNIQUE (source_ref),
    CONSTRAINT ck_problem_question_source_type
        CHECK (source_type IN ('IMPORTED', 'GENERATED', 'RUNTIME')),
    CONSTRAINT ck_problem_question_difficulty CHECK (difficulty BETWEEN 1 AND 3),
    CONSTRAINT ck_problem_question_type
        CHECK (question_type IN ('MULTIPLE_CHOICE', 'SHORT_INPUT', 'STEP_FILL', 'ESSAY')),
    CONSTRAINT ck_problem_question_presentation
        CHECK (presentation IN ('TEXT_ONLY', 'WITH_FIGURE', 'WITH_TABLE')),
    CONSTRAINT ck_problem_question_verification_status
        CHECK (verification_status IN ('PENDING', 'PASSED', 'MISMATCH', 'ERROR')),
    CONSTRAINT ck_problem_question_verification_attempts
        CHECK (verification_attempts >= 0)
);

CREATE INDEX idx_problem_question_sub_unit_difficulty
    ON problem_question (sub_unit_id, difficulty);
CREATE INDEX idx_problem_question_topic ON problem_question (topic_code);
CREATE INDEX idx_problem_question_derived_from
    ON problem_question (derived_from_question_id);

COMMENT ON TABLE problem_question IS '문항 본체. IMPORTED(문제은행 적재) / GENERATED(교사 생성) / RUNTIME(서술형 즉석 생성)이 한 테이블에 들어간다';
COMMENT ON COLUMN problem_question.source_ref IS '{데이터셋번호}:{원천ID}. GENERATED·RUNTIME은 NULL (PG unique는 NULL 다중 허용)';
COMMENT ON COLUMN problem_question.question_type IS 'SHORT_INPUT 포함 여부는 팀 미결. 값을 좁히는 후속 마이그레이션은 쉽지만 넓히는 것은 재작업이라 우선 포함했다';
COMMENT ON COLUMN problem_question.content_blocks IS '렌더링 정본. 내부 assetRef가 problem_asset.asset_key를 가리킨다';
COMMENT ON COLUMN problem_question.prompt_text IS 'RAG 검색·임베딩 전용 원문. 화면 표시 금지 — 발문 표시는 content_blocks의 첫 TEXT 블록';
COMMENT ON COLUMN problem_question.verification_status IS 'NULL=검증 대상 아님(IMPORTED·서술형). PENDING/PASSED/MISMATCH(재생성 후에도 불일치)/ERROR(수행 실패)';

-- 빈칸형(STEP_FILL) 문항의 풀이 단계. 한 문항이 1~4단계로 나뉜다.
CREATE TABLE problem_step
(
    id            BIGSERIAL    PRIMARY KEY,
    question_id   BIGINT       NOT NULL,
    display_order SMALLINT     NOT NULL,
    label         VARCHAR(100),
    segments      JSONB        NOT NULL,
    CONSTRAINT fk_problem_step_question
        FOREIGN KEY (question_id) REFERENCES problem_question (id),
    CONSTRAINT uk_problem_step_question_order UNIQUE (question_id, display_order),
    CONSTRAINT ck_problem_step_display_order CHECK (display_order >= 0)
);

COMMENT ON TABLE problem_step IS '빈칸형 문항의 풀이 단계. STEP_FILL만 행을 가진다(서비스 검증)';
COMMENT ON COLUMN problem_step.display_order IS '원천 order는 1부터이므로 적재 시 0부터 재부여한다';
COMMENT ON COLUMN problem_step.segments IS '{type:TEXT,value} / {type:BLANK,unitKey} 배열. unitKey 집합 = 그 단계의 problem_answer_unit.unit_key 집합(적재 전 검증)';

-- 채점의 최소 단위. 객관식·서술형은 MAIN 1행, 빈칸형은 빈칸 수만큼.
CREATE TABLE problem_answer_unit
(
    id                BIGSERIAL   PRIMARY KEY,
    question_id       BIGINT      NOT NULL,
    step_id           BIGINT,
    unit_key          VARCHAR(20) NOT NULL,
    display_order     INTEGER     NOT NULL,
    label             VARCHAR(100),
    answer_raw        TEXT,
    answer_normalized TEXT,
    compare_method    VARCHAR(20) NOT NULL,
    diagnostic_type   VARCHAR(20),
    display_unit      VARCHAR(20),
    CONSTRAINT fk_problem_answer_unit_question
        FOREIGN KEY (question_id) REFERENCES problem_question (id),
    CONSTRAINT fk_problem_answer_unit_step
        FOREIGN KEY (step_id) REFERENCES problem_step (id),
    CONSTRAINT uk_problem_answer_unit_question_key UNIQUE (question_id, unit_key),
    CONSTRAINT ck_problem_answer_unit_compare_method
        CHECK (compare_method IN ('CHOICE', 'VALUE', 'EXACT', 'SET', 'SUBST', 'RUBRIC')),
    CONSTRAINT ck_problem_answer_unit_diagnostic_type
        CHECK (diagnostic_type IN ('INTERPRET', 'MODEL', 'EXECUTE', 'ANSWER')),
    CONSTRAINT ck_problem_answer_unit_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_problem_answer_unit_step ON problem_answer_unit (step_id);

COMMENT ON TABLE problem_answer_unit IS '채점 단위(정답). 정답은 이 테이블에만 있다';
COMMENT ON COLUMN problem_answer_unit.unit_key IS '빈칸형 B1, B2… / 객관식·서술형은 MAIN';
COMMENT ON COLUMN problem_answer_unit.answer_raw IS 'ESSAY만 NULL(모범답안 미저장). 객관식은 1 기준 보기 번호 — 채점은 problem_choice.display_order + 1과 비교';
COMMENT ON COLUMN problem_answer_unit.diagnostic_type IS '취약점 분석용 4분류. stages[].order로 추론 금지(실측상 title과만 1:1)';

-- 객관식 보기. 정답 표시 컬럼이 의도적으로 없다.
CREATE TABLE problem_choice
(
    id            BIGSERIAL PRIMARY KEY,
    question_id   BIGINT    NOT NULL,
    display_order SMALLINT  NOT NULL,
    content       TEXT      NOT NULL,
    CONSTRAINT fk_problem_choice_question
        FOREIGN KEY (question_id) REFERENCES problem_question (id),
    CONSTRAINT uk_problem_choice_question_order UNIQUE (question_id, display_order),
    CONSTRAINT ck_problem_choice_display_order CHECK (display_order >= 0)
);

COMMENT ON TABLE problem_choice IS '객관식 보기. 정답 여부 컬럼 없음 — 정답은 problem_answer_unit.answer_raw에만';

-- 문항에 딸린 이미지(도형·수식·표).
CREATE TABLE problem_asset
(
    id            BIGSERIAL    PRIMARY KEY,
    question_id   BIGINT       NOT NULL,
    asset_key     VARCHAR(16)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    display_order SMALLINT     NOT NULL,
    storage_key   VARCHAR(255) NOT NULL,
    width_px      INTEGER      NOT NULL,
    height_px     INTEGER      NOT NULL,
    alt_text      TEXT,
    CONSTRAINT fk_problem_asset_question
        FOREIGN KEY (question_id) REFERENCES problem_question (id),
    CONSTRAINT uk_problem_asset_question_key UNIQUE (question_id, asset_key),
    CONSTRAINT ck_problem_asset_role CHECK (role IN ('FIGURE', 'FORMULA', 'TABLE')),
    CONSTRAINT ck_problem_asset_display_order CHECK (display_order >= 0)
);

COMMENT ON TABLE problem_asset IS '문항 이미지. 공개 버킷 저장(학생 답안 이미지 비공개 버킷과 절대 분리)';
COMMENT ON COLUMN problem_asset.asset_key IS '원천 assets[].assetId. content_blocks의 assetRef와 조인하는 키';
COMMENT ON COLUMN problem_asset.role IS 'FORMULA 크롭은 적재 제외 확정(블록 전건 미참조) — 값은 확장 대비로 유지';

-- 서술형 채점 기준. 항목별 가중치를 갖고 LLM 판정 후 백엔드가 점수를 계산한다.
CREATE TABLE problem_rubric_item
(
    id            BIGSERIAL PRIMARY KEY,
    question_id   BIGINT    NOT NULL,
    display_order INTEGER   NOT NULL,
    label         TEXT      NOT NULL,
    weight        SMALLINT  NOT NULL,
    approved_by   BIGINT,
    approved_at   TIMESTAMPTZ,
    CONSTRAINT fk_problem_rubric_item_question
        FOREIGN KEY (question_id) REFERENCES problem_question (id),
    CONSTRAINT fk_problem_rubric_item_approved_by
        FOREIGN KEY (approved_by) REFERENCES member_account (id),
    CONSTRAINT uk_problem_rubric_item_question_order UNIQUE (question_id, display_order),
    CONSTRAINT ck_problem_rubric_item_weight CHECK (weight >= 0),
    CONSTRAINT ck_problem_rubric_item_display_order CHECK (display_order >= 0)
);

COMMENT ON TABLE problem_rubric_item IS '서술형(ESSAY) 채점 기준. 미승인(approved_at IS NULL) 루브릭은 배포 차단(서비스)';
COMMENT ON COLUMN problem_rubric_item.weight IS '문항당 가중치 합 > 0 은 서비스가 검증한다';

-- ============================================================
-- D. 학습지 (worksheet 도메인) — 배정 포함
-- ============================================================

-- 문항 묶음. 문제 보관함 저장 시점에 확정되며 이후 불변.
CREATE TABLE worksheet
(
    id                   BIGSERIAL    PRIMARY KEY,
    title                VARCHAR(100) NOT NULL,
    type                 VARCHAR(30)  NOT NULL,
    origin               VARCHAR(10)  NOT NULL,
    owner_teacher_id     BIGINT       NOT NULL,
    grade                SMALLINT     NOT NULL,
    semester             VARCHAR(10)  NOT NULL,
    total_score          SMALLINT,
    source_assignment_id BIGINT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,
    CONSTRAINT fk_worksheet_owner_teacher
        FOREIGN KEY (owner_teacher_id) REFERENCES member_account (id),
    CONSTRAINT ck_worksheet_type
        CHECK (type IN ('GENERAL_LEARNING', 'COMPREHENSIVE_ASSESSMENT')),
    CONSTRAINT ck_worksheet_origin CHECK (origin IN ('STANDARD', 'CUSTOM')),
    CONSTRAINT ck_worksheet_grade CHECK (grade BETWEEN 1 AND 3),
    CONSTRAINT ck_worksheet_semester CHECK (semester IN ('1', '2', 'COMMON'))
);
-- source_assignment_id FK는 worksheet_assignment 생성 후 파일 하단에서 추가한다(순환 참조).

CREATE INDEX idx_worksheet_owner_teacher ON worksheet (owner_teacher_id);
CREATE INDEX idx_worksheet_source_assignment ON worksheet (source_assignment_id);

COMMENT ON TABLE worksheet IS '학습지. type(일반/종합평가) × origin(일반/맞춤) 조합';
COMMENT ON COLUMN worksheet.type IS '노션 ERD는 varchar(20)이나 COMPREHENSIVE_ASSESSMENT가 24자라 30으로 정정 — 노션 반영 필요';
COMMENT ON COLUMN worksheet.total_score IS '종합평가만 100 고정(서비스 검증). 일반·맞춤은 NULL';
COMMENT ON COLUMN worksheet.source_assignment_id IS 'CUSTOM에서만 값. 어느 배포 결과를 보고 만들었는지(학습지가 아니라 배정을 가리킨다)';

-- 출제 조건. 소단원 × 난이도별 문항 수 — 복제 후 재구성의 입력값.
CREATE TABLE worksheet_gen_spec
(
    id             BIGSERIAL PRIMARY KEY,
    worksheet_id   BIGINT    NOT NULL,
    sub_unit_id    BIGINT    NOT NULL,
    difficulty     SMALLINT  NOT NULL,
    question_count SMALLINT  NOT NULL,
    CONSTRAINT fk_worksheet_gen_spec_worksheet
        FOREIGN KEY (worksheet_id) REFERENCES worksheet (id),
    CONSTRAINT fk_worksheet_gen_spec_sub_unit
        FOREIGN KEY (sub_unit_id) REFERENCES curriculum_unit (id),
    CONSTRAINT uk_worksheet_gen_spec UNIQUE (worksheet_id, sub_unit_id, difficulty),
    CONSTRAINT ck_worksheet_gen_spec_difficulty CHECK (difficulty BETWEEN 1 AND 3),
    CONSTRAINT ck_worksheet_gen_spec_question_count CHECK (question_count >= 0)
);

COMMENT ON TABLE worksheet_gen_spec IS '출제 조건. [미결] 종합평가의 문항 유형 축이 없다 — 유형별 출제 조건 저장처는 팀 미결';

-- 학습지 내부 문항 목록.
CREATE TABLE worksheet_item
(
    id            BIGSERIAL     PRIMARY KEY,
    worksheet_id  BIGINT        NOT NULL,
    question_id   BIGINT        NOT NULL,
    display_order INTEGER       NOT NULL,
    max_score     NUMERIC(5, 2),
    custom_stage  VARCHAR(20),
    support_mode  VARCHAR(20),
    CONSTRAINT fk_worksheet_item_worksheet
        FOREIGN KEY (worksheet_id) REFERENCES worksheet (id),
    CONSTRAINT fk_worksheet_item_question
        FOREIGN KEY (question_id) REFERENCES problem_question (id),
    CONSTRAINT uk_worksheet_item_question UNIQUE (worksheet_id, question_id),
    CONSTRAINT uk_worksheet_item_order UNIQUE (worksheet_id, display_order),
    CONSTRAINT ck_worksheet_item_custom_stage
        CHECK (custom_stage IN ('REVIEW', 'SIMILAR', 'ADVANCED')),
    CONSTRAINT ck_worksheet_item_support_mode
        CHECK (support_mode IN ('CHATBOT', 'CONCEPT_GUIDE')),
    CONSTRAINT ck_worksheet_item_display_order CHECK (display_order >= 0)
);

COMMENT ON COLUMN worksheet_item.max_score IS '종합평가만 값. 배점 합 = worksheet.total_score(서비스 검증), 끝수는 마지막 문항에';
COMMENT ON COLUMN worksheet_item.custom_stage IS '맞춤 학습만 값. 복습/유사/응용';
COMMENT ON COLUMN worksheet_item.support_mode IS 'CHATBOT/CONCEPT_GUIDE 배타(단일 컬럼이라 구조적으로 보장). [미결] 배타 정책 자체는 StudentSolvePage 확인 대기';

-- 배포 행위. 반 단위(일반·종합) 또는 학생 단위(맞춤) 택일.
CREATE TABLE worksheet_assignment
(
    id           BIGSERIAL   PRIMARY KEY,
    worksheet_id BIGINT      NOT NULL,
    class_id     BIGINT,
    student_id   BIGINT,
    assigned_at  TIMESTAMPTZ NOT NULL,
    due_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_worksheet_assignment_worksheet
        FOREIGN KEY (worksheet_id) REFERENCES worksheet (id),
    CONSTRAINT fk_worksheet_assignment_class
        FOREIGN KEY (class_id) REFERENCES member_school_class (id),
    CONSTRAINT fk_worksheet_assignment_student
        FOREIGN KEY (student_id) REFERENCES member_account (id),
    CONSTRAINT uk_worksheet_assignment_worksheet_class UNIQUE (worksheet_id, class_id),
    CONSTRAINT uk_worksheet_assignment_worksheet_student UNIQUE (worksheet_id, student_id),
    CONSTRAINT ck_worksheet_assignment_target_xor
        CHECK ((class_id IS NULL) <> (student_id IS NULL))
);

CREATE INDEX idx_worksheet_assignment_class ON worksheet_assignment (class_id);
CREATE INDEX idx_worksheet_assignment_student ON worksheet_assignment (student_id);

COMMENT ON TABLE worksheet_assignment IS '배포 행위. XOR CHECK로 반/학생 택일을 DB에서도 보장. 중복 배포는 UNIQUE 2종이 차단(PG unique는 NULL 다중 허용이라 성립)';

-- 학생 한 명의 진행 상태. 배포와 동시에 반 인원수만큼 생성.
CREATE TABLE worksheet_assignment_student
(
    id             BIGSERIAL     PRIMARY KEY,
    assignment_id  BIGINT        NOT NULL,
    student_id     BIGINT        NOT NULL,
    status         VARCHAR(15)   NOT NULL,
    progress_count SMALLINT      NOT NULL DEFAULT 0,
    submitted_at   TIMESTAMPTZ,
    graded_at      TIMESTAMPTZ,
    released_at    TIMESTAMPTZ,
    total_score    NUMERIC(5, 2),
    CONSTRAINT fk_worksheet_assignment_student_assignment
        FOREIGN KEY (assignment_id) REFERENCES worksheet_assignment (id),
    CONSTRAINT fk_worksheet_assignment_student_student
        FOREIGN KEY (student_id) REFERENCES member_student_profile (user_id),
    CONSTRAINT uk_worksheet_assignment_student UNIQUE (assignment_id, student_id),
    CONSTRAINT ck_worksheet_assignment_student_status
        CHECK (status IN ('NOT_STARTED', 'SUBMITTED', 'GRADED', 'NOT_SUBMITTED')),
    CONSTRAINT ck_worksheet_assignment_student_progress CHECK (progress_count >= 0)
);

CREATE INDEX idx_worksheet_assignment_student_student
    ON worksheet_assignment_student (student_id);

COMMENT ON TABLE worksheet_assignment_student IS '학생별 배정. 제출·채점·공개 상태의 정본';
COMMENT ON COLUMN worksheet_assignment_student.released_at IS '교사가 반 단위 확정을 누른 시각. NULL이면 학생은 점수·정답·해설을 볼 수 없다';
COMMENT ON COLUMN worksheet_assignment_student.total_score IS '종합평가 전용 비정규화. 안전 근거는 단일 경로 갱신 + 정정 시 동일 트랜잭션 재계산(확정 후 정정 허용)';

-- 순환 FK 해소: 맞춤 학습지의 출처 배정.
ALTER TABLE worksheet
    ADD CONSTRAINT fk_worksheet_source_assignment
        FOREIGN KEY (source_assignment_id) REFERENCES worksheet_assignment (id);

-- ============================================================
-- E. 제출 (submission 도메인)
-- ============================================================

-- 학생 답안. 채점 칸마다 한 행. 제출 시점에 일괄 생성.
CREATE TABLE submission_answer
(
    id                    BIGSERIAL     PRIMARY KEY,
    assignment_student_id BIGINT        NOT NULL,
    answer_unit_id        BIGINT        NOT NULL,
    input_mode            VARCHAR(20)   NOT NULL,
    selected_choice_id    BIGINT,
    raw_latex             TEXT,
    normalized            TEXT,
    answer_image_ref      VARCHAR(255),
    auto_score            NUMERIC(5, 2),
    final_score           NUMERIC(5, 2),
    overridden_by         BIGINT,
    overridden_at         TIMESTAMPTZ,
    compare_method        VARCHAR(20)   NOT NULL,
    grading_status        VARCHAR(20)   NOT NULL,
    failure_reason        VARCHAR(100),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_submission_answer_assignment_student
        FOREIGN KEY (assignment_student_id) REFERENCES worksheet_assignment_student (id),
    CONSTRAINT fk_submission_answer_answer_unit
        FOREIGN KEY (answer_unit_id) REFERENCES problem_answer_unit (id),
    CONSTRAINT fk_submission_answer_selected_choice
        FOREIGN KEY (selected_choice_id) REFERENCES problem_choice (id),
    CONSTRAINT fk_submission_answer_overridden_by
        FOREIGN KEY (overridden_by) REFERENCES member_account (id),
    CONSTRAINT uk_submission_answer UNIQUE (assignment_student_id, answer_unit_id),
    CONSTRAINT ck_submission_answer_input_mode
        CHECK (input_mode IN ('CHOICE', 'HANDWRITING', 'IMAGE')),
    CONSTRAINT ck_submission_answer_compare_method
        CHECK (compare_method IN ('CHOICE', 'VALUE', 'EXACT', 'SET', 'SUBST', 'RUBRIC')),
    CONSTRAINT ck_submission_answer_grading_status
        CHECK (grading_status IN ('NOT_GRADED', 'GRADED', 'FAILED'))
);

CREATE INDEX idx_submission_answer_answer_unit ON submission_answer (answer_unit_id);

COMMENT ON TABLE submission_answer IS '학생 답안. 칸 단위 오답률 집계 축은 answer_unit_id 인덱스';
COMMENT ON COLUMN submission_answer.auto_score IS '자동채점값. 최초 기록 후 불변 — 채점기 결함을 compare_method별 오답률로 측정하는 안전장치';
COMMENT ON COLUMN submission_answer.final_score IS '기본값은 auto_score 복사. 교사 수정 시 overridden_by/at 필수. 확정 후 수정은 정정으로 허용(overridden_at > released_at으로 파생)';
COMMENT ON COLUMN submission_answer.compare_method IS '실제 적용된 채점 방법의 스냅샷';

-- 문항별 풀이 시간. 되돌아가기가 가능하므로 누적 체류 시간이다.
CREATE TABLE submission_question_time
(
    id                    BIGSERIAL PRIMARY KEY,
    assignment_student_id BIGINT    NOT NULL,
    worksheet_item_id     BIGINT    NOT NULL,
    time_spent_seconds    INTEGER   NOT NULL,
    CONSTRAINT fk_submission_question_time_assignment_student
        FOREIGN KEY (assignment_student_id) REFERENCES worksheet_assignment_student (id),
    CONSTRAINT fk_submission_question_time_worksheet_item
        FOREIGN KEY (worksheet_item_id) REFERENCES worksheet_item (id),
    CONSTRAINT uk_submission_question_time UNIQUE (assignment_student_id, worksheet_item_id),
    CONSTRAINT ck_submission_question_time_seconds CHECK (time_spent_seconds >= 0)
);

CREATE INDEX idx_submission_question_time_worksheet_item
    ON submission_question_time (worksheet_item_id);

COMMENT ON TABLE submission_question_time IS '문항별 누적 풀이 시간. 클라이언트 측정이라 참고 지표 — 채점·점수에 미관여. 미제출 학생은 행이 없다(답안 행과 비대칭)';

-- ============================================================
-- E. 채점 (grading 도메인)
-- ============================================================

-- 서술형 채점 항목별 판정 결과.
CREATE TABLE grading_rubric_result
(
    id                BIGSERIAL PRIMARY KEY,
    student_answer_id BIGINT    NOT NULL,
    rubric_item_id    BIGINT    NOT NULL,
    satisfied         BOOLEAN   NOT NULL,
    evidence          TEXT,
    CONSTRAINT fk_grading_rubric_result_answer
        FOREIGN KEY (student_answer_id) REFERENCES submission_answer (id),
    CONSTRAINT fk_grading_rubric_result_rubric_item
        FOREIGN KEY (rubric_item_id) REFERENCES problem_rubric_item (id),
    CONSTRAINT uk_grading_rubric_result UNIQUE (student_answer_id, rubric_item_id)
);

CREATE INDEX idx_grading_rubric_result_rubric_item
    ON grading_rubric_result (rubric_item_id);

COMMENT ON TABLE grading_rubric_result IS '서술형 LLM 판정 결과. 채점 실패 시 행을 만들지 않는다 — 행 부재=판정 안 함, false=미충족. 교사가 점수만 정정해도 판정은 보존(의도된 불일치)';
