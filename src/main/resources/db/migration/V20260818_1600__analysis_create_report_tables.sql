-- analysis 도메인 AI 분석 보고서.
--
-- 학생 한 명의 학습지 수행(worksheet_assignment_student)에 대해 AI가 생성한 문장만 저장한다.
-- 점수·정답률·학급 평균·풀이시간·문항 번호 같은 분석 원본 수치는 여기에 두지 않고
-- 기존 worksheet / submission 테이블에서 조회한다.
--
-- V20260807_1000 의 analysis_report 와 이름만 같고 구조는 무관하다. 그 테이블은
-- V20260810_1023 에서 제거되었다.
--
-- 시각 타입: created_at / updated_at 은 BaseTimeEntity 가 LocalDateTime 으로 고정하므로
-- timestamp, 실제 생성 사건 시각인 generated_at 은 timestamptz 를 쓴다.

-- 학생 학습지 수행 한 회차의 보고서. 과거 버전을 보관하지 않고 같은 행을 갱신한다.
CREATE TABLE analysis_report
(
    id                    BIGSERIAL   PRIMARY KEY,
    assignment_student_id BIGINT      NOT NULL,
    generation_status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    summary_message       TEXT,
    overall_observation   TEXT,
    prompt_version        VARCHAR(20),
    model_name            VARCHAR(50),
    llm_schema_version    SMALLINT,
    last_error_code       VARCHAR(100),
    generated_at          TIMESTAMPTZ,
    created_at            TIMESTAMP   NOT NULL,
    updated_at            TIMESTAMP   NOT NULL,
    CONSTRAINT fk_analysis_report_assignment_student
        FOREIGN KEY (assignment_student_id)
        REFERENCES worksheet_assignment_student(id),
    CONSTRAINT uk_analysis_report_assignment_student
        UNIQUE (assignment_student_id),
    CONSTRAINT ck_analysis_report_generation_status
        CHECK (generation_status IN ('PENDING', 'GENERATING', 'READY', 'FAILED')),
    CONSTRAINT ck_analysis_report_ready_messages
        CHECK (generation_status <> 'READY'
            OR (summary_message IS NOT NULL AND overall_observation IS NOT NULL))
);

COMMENT ON TABLE analysis_report IS '학생 학습지 수행 한 회차의 AI 분석 문장';
COMMENT ON COLUMN analysis_report.generation_status IS
    'PENDING / GENERATING / READY / FAILED. READY 면 두 문장이 모두 존재한다';
COMMENT ON COLUMN analysis_report.generated_at IS
    '현재 저장된 문장이 마지막으로 생성된 시각. 채점 시각과 비교해 재생성 필요를 판단한다';
COMMENT ON COLUMN analysis_report.prompt_version IS
    '문장을 만든 프롬프트 버전. 품질 문의가 왔을 때 어느 프롬프트 결과인지 재현한다';
COMMENT ON COLUMN analysis_report.llm_schema_version IS
    'LLM 출력 계약의 schemaVersion 을 그대로 저장한다. 계약이 바뀔 때 기존 행을 구분한다';
COMMENT ON COLUMN analysis_report.last_error_code IS
    'FAILED 원인. 호출 실패·형식 오류·검증 실패·비동기 거부를 구분한다';

-- 문항별 AI 문장. 재생성 시 해당 보고서의 행을 모두 지우고 다시 넣는다.
CREATE TABLE analysis_report_item_message
(
    id                 BIGSERIAL PRIMARY KEY,
    analysis_report_id BIGINT    NOT NULL,
    worksheet_item_id  BIGINT    NOT NULL,
    observation        TEXT      NOT NULL,
    learning_point     TEXT      NOT NULL,
    retry_guide        TEXT      NOT NULL,
    created_at         TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP NOT NULL,
    CONSTRAINT fk_analysis_report_item_message_report
        FOREIGN KEY (analysis_report_id) REFERENCES analysis_report(id),
    CONSTRAINT fk_analysis_report_item_message_worksheet_item
        FOREIGN KEY (worksheet_item_id) REFERENCES worksheet_item(id),
    CONSTRAINT uk_analysis_report_item_message
        UNIQUE (analysis_report_id, worksheet_item_id)
);

COMMENT ON TABLE analysis_report_item_message IS '문항별 AI 분석 문장';
COMMENT ON COLUMN analysis_report_item_message.observation IS
    '화면의 확인된 점. 해당 문항에서 실제로 확인된 풀이 행동';
COMMENT ON COLUMN analysis_report_item_message.learning_point IS
    '화면의 학습 포인트. 개념명이 아니라 익혀야 할 행동을 ~하기 형태로 저장한다';
COMMENT ON COLUMN analysis_report_item_message.retry_guide IS
    '화면의 다시 풀 때. 교사가 학생에게 시킬 구체적인 재풀이 활동';
