-- analysis 도메인 baseline.
--
-- edu-sen 프로토타입의 edu_sen.student_assessments / student_attempts / student_reports 를
-- backend 규칙(도메인 접두어 analysis_)으로 옮긴 것이다.
--
-- 식별자 정책: edu-sen 이 쓰던 문자열 식별자(assessment_id, student_id, event_id)를 업무 키로
-- 그대로 두고, JPA 용 대리 키 id 를 따로 둔다. member / problem 도메인이 아직 없어서 지금은
-- 참조할 Long PK 자체가 존재하지 않는다. 해당 도메인이 생기면 FK 컬럼을 덧붙인다.
--
-- 시각 타입: created_at / updated_at 은 BaseTimeEntity 가 LocalDateTime 으로 고정하므로
-- timestamp, 실제 사건 시각은 timestamptz 를 쓴다.

-- 문제지 한 회분. 학생 한 명이 한 회차를 푼 단위이며 (assessment_id, student_id) 가 업무 키다.
CREATE TABLE analysis_assessment
(
    id               BIGSERIAL    PRIMARY KEY,
    assessment_id    VARCHAR(64)  NOT NULL,
    student_id       VARCHAR(64)  NOT NULL,
    assessment_title VARCHAR(255) NOT NULL,
    assessment_date  DATE         NOT NULL,
    student_name     VARCHAR(100) NOT NULL,
    assessment_type  VARCHAR(40)  NOT NULL,
    is_simulation    BOOLEAN      NOT NULL DEFAULT FALSE,
    status           VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    CONSTRAINT uk_analysis_assessment_business_key UNIQUE (assessment_id, student_id)
);

COMMENT ON TABLE analysis_assessment IS '학생 한 명의 문제지 한 회분';
COMMENT ON COLUMN analysis_assessment.status IS 'IN_PROGRESS / COMPLETED. 완료되면 풀이를 더 받지 않는다';
COMMENT ON COLUMN analysis_assessment.is_simulation IS '모의 데이터 여부. 집계에서 실제 데이터와 섞지 않는다';

-- 문항 단위 풀이 시도. event_id 가 멱등 키라서 같은 이벤트가 두 번 들어와도 한 번만 저장된다.
CREATE TABLE analysis_attempt
(
    id                     BIGSERIAL    PRIMARY KEY,
    event_id               VARCHAR(64)  NOT NULL,
    assessment_id          VARCHAR(64)  NOT NULL,
    student_id             VARCHAR(64)  NOT NULL,
    problem_number         INTEGER      NOT NULL,
    problem_id             VARCHAR(64),
    problem_title          VARCHAR(255),
    concept_id             VARCHAR(64),
    step_id                VARCHAR(64),
    purpose                VARCHAR(20)  NOT NULL DEFAULT 'DIAGNOSTIC',
    is_correct             BOOLEAN      NOT NULL DEFAULT FALSE,
    hint_used              BOOLEAN      NOT NULL DEFAULT FALSE,
    submission_failed      BOOLEAN      NOT NULL DEFAULT FALSE,
    source_dataset         VARCHAR(64),
    evaluation_area        VARCHAR(100),
    topic                  VARCHAR(255),
    reference_success_rate NUMERIC(5, 4),
    difficulty_band        VARCHAR(20),
    source_difficulty      VARCHAR(40),
    difficulty_basis       VARCHAR(255),
    problem_text           TEXT,
    problem_image_url      TEXT,
    choices_json           JSONB        NOT NULL DEFAULT '[]'::jsonb,
    response_type          VARCHAR(20),
    student_answer         TEXT,
    correct_answer         TEXT,
    step_responses_json    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    occurred_at            TIMESTAMPTZ  NOT NULL,
    created_at             TIMESTAMP    NOT NULL,
    updated_at             TIMESTAMP    NOT NULL,
    CONSTRAINT uk_analysis_attempt_event UNIQUE (event_id)
);

-- 보고서·대시보드·취약점 분석이 모두 (회차, 학생)으로 읽고 문항 순서대로 정렬한다.
CREATE INDEX idx_analysis_attempt_lookup
    ON analysis_attempt (assessment_id, student_id, problem_number, occurred_at);

COMMENT ON TABLE analysis_attempt IS '문항 단위 풀이 시도';
COMMENT ON COLUMN analysis_attempt.event_id IS '멱등 키. 같은 이벤트 재전송을 중복 저장하지 않는다';
COMMENT ON COLUMN analysis_attempt.submission_failed IS '제출 자체가 기록되지 않은 문항. 보고서·집계에서 제외한다';
COMMENT ON COLUMN analysis_attempt.purpose IS
    'DIAGNOSTIC / APPLIED. 진단 응답만 취약점 상태를 움직인다. 응용 오답은 새 맥락을 읽지 못한 것인지 '
    '개념을 잊은 것인지 구분할 수 없어 취약 근거로 쓰지 않는다. 기본값을 진단으로 두어 값을 채우지 않는 '
    '경로도 지금까지와 같게 동작한다';
COMMENT ON COLUMN analysis_attempt.evaluation_area IS '평가 영역. 풀이 단계(step_id)와는 다른 축이며 합치지 않는다';
COMMENT ON COLUMN analysis_attempt.step_id IS '풀이 단계. 평가 영역(evaluation_area)과는 다른 축이며 합치지 않는다';

-- 생성된 보고서. AI 초안 → 교사 수정 → 교사 확정 순으로 상태가 바뀌고 version 으로 낙관적 잠금을 건다.
CREATE TABLE analysis_report
(
    id                      BIGSERIAL   PRIMARY KEY,
    report_id               UUID        NOT NULL,
    assessment_id           VARCHAR(64) NOT NULL,
    student_id              VARCHAR(64) NOT NULL,
    report_type             VARCHAR(40) NOT NULL,
    report_status           VARCHAR(30) NOT NULL DEFAULT 'AI_DRAFT',
    status_name             VARCHAR(100),
    facts_json              JSONB       NOT NULL DEFAULT '{}'::jsonb,
    narrative_json          JSONB       NOT NULL DEFAULT '{}'::jsonb,
    llm_call_json           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    generated_sections_json JSONB       NOT NULL DEFAULT '[]'::jsonb,
    edited_sections_json    JSONB       NOT NULL DEFAULT '[]'::jsonb,
    html_path               TEXT,
    pdf_path                TEXT,
    version                 INTEGER     NOT NULL DEFAULT 0,
    confirmed_by            VARCHAR(64),
    confirmed_at            TIMESTAMPTZ,
    created_at              TIMESTAMP   NOT NULL,
    updated_at              TIMESTAMP   NOT NULL,
    CONSTRAINT uk_analysis_report_report_id UNIQUE (report_id)
);

-- 학생 화면은 (학생, 회차)로, 목록 화면은 생성 역순으로 읽는다.
CREATE INDEX idx_analysis_report_student
    ON analysis_report (student_id, assessment_id, report_type);
CREATE INDEX idx_analysis_report_created
    ON analysis_report (created_at DESC);

COMMENT ON TABLE analysis_report IS '생성된 학생 보고서';
COMMENT ON COLUMN analysis_report.report_status IS 'AI_DRAFT / TEACHER_EDITED / TEACHER_CONFIRMED';
COMMENT ON COLUMN analysis_report.version IS '낙관적 잠금. 교사 두 명이 같은 보고서를 동시에 저장하는 것을 막는다';
COMMENT ON COLUMN analysis_report.llm_call_json IS 'LLM 호출 요청·응답 기록. edu-sen 의 openai_call_json 을 제공자 중립 이름으로 옮긴 것';
