-- 개념 챗봇(SOLVE_CHAT / REVIEW_CHAT)의 컨텍스트 소스.
--
-- 소유는 chat 도메인이다. 테이블 접두어도 chat_ 로 붙였다(AGENTS.md 6절).
-- sub_unit_id 만 curriculum_unit 을 FK 로 참조하는데, 그 테이블은 curriculum 도메인 소유이며
-- 이 마이그레이션은 그쪽 컬럼·데이터를 건드리지 않는다.
--
-- 원천: AI Hub 133번 수학 지식체계 데이터셋. 개념 노드 1,631개 / 선수관계 엣지 3,446개에서
-- 중1 범위와 그 선수 개념만 추린 것이다.
-- 산출 과정과 측정값: EduCenDocs 의 docs/measurements/DATA_PIPELINE_133.md
--
-- 이 파일은 구조만 만든다. 데이터는 V20260812_1730 이 넣는다.
-- 그 시드가 curriculum_unit 30행을 만드는 V20260812_1628 뒤에 와야 하므로, 짝인 이 DDL 도
-- 함께 1628 뒤로 옮겼다. 두 파일의 상대 순서(DDL -> 시드)는 유지한다.
--
-- 적재 규모:
--   chat_concept        455행 (중1 210 + 초등 245)
--   chat_concept_prereq 1,098행
--
-- 벡터 검색을 쓰지 않는다. 검색은 이름 매칭 + 이 그래프의 재귀 확장으로 한다.
-- 선수관계의 84%는 개념 정의문에 언급되지 않아(실측) 텍스트 유사도로는 도달할 수 없다.
-- 이 테이블이 있는 이유가 그것이다.

CREATE TABLE chat_concept
(
    id                BIGSERIAL    PRIMARY KEY,
    source_concept_id INTEGER      NOT NULL,
    name              VARCHAR(200) NOT NULL,
    sub_unit_id       BIGINT,
    grade_band        VARCHAR(12)  NOT NULL,
    source_semester   VARCHAR(30),
    elem_hop          SMALLINT,
    description       TEXT         NOT NULL,
    achievement_2015  TEXT,
    moved_from_grade  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_chat_concept_sub_unit
        FOREIGN KEY (sub_unit_id) REFERENCES curriculum_unit (id),
    CONSTRAINT uk_chat_concept_source_id UNIQUE (source_concept_id),
    CONSTRAINT ck_chat_concept_grade_band
        CHECK (grade_band IN ('MIDDLE_1', 'ELEMENTARY')),
    -- 중1 개념은 소단원에 반드시 매달리고, 초등 개념은 EBS 트리 밖이라 매달릴 수 없다.
    CONSTRAINT ck_chat_concept_band_shape
        CHECK ((grade_band = 'MIDDLE_1'   AND sub_unit_id IS NOT NULL AND elem_hop IS NULL)
            OR (grade_band = 'ELEMENTARY' AND sub_unit_id IS     NULL AND elem_hop IS NOT NULL)),
    CONSTRAINT ck_chat_concept_elem_hop CHECK (elem_hop >= 1)
);

CREATE INDEX idx_chat_concept_sub_unit ON chat_concept (sub_unit_id);
CREATE INDEX idx_chat_concept_name     ON chat_concept (name);

COMMENT ON TABLE chat_concept IS '개념 마스터 455행. 조회 전용이라 타임스탬프·소프트 삭제가 없다';
COMMENT ON COLUMN chat_concept.source_concept_id IS '133번 원천 concept id. 시드 재생성 시 대조하는 유일한 축';
COMMENT ON COLUMN chat_concept.name IS '고유하지 않다. 원천에서 81개 이름이 학년별로 중복된다(거듭제곱=중1/중2/고등). 이름으로 조인 금지';
COMMENT ON COLUMN chat_concept.sub_unit_id IS '초등 개념은 NULL. 소단원 레벨인지는 서비스가 검증한다';
COMMENT ON COLUMN chat_concept.elem_hop IS '중1 개념에서 선수 방향으로 몇 걸음인지. 초등만 값이 있다. 주입 기본 정책은 1 이하';
COMMENT ON COLUMN chat_concept.description IS 'LLM에 주입하는 본문. 원천 문자열 그대로이며 59%가 LaTeX를 포함한다. 줄바꿈이 실제 개행이 아니라 리터럴 두 글자(\n)라 주입 직전 치환이 필요하다';
COMMENT ON COLUMN chat_concept.achievement_2015 IS '2015 성취기준 문장. 코드가 아니라 문장이며 원천에 코드가 없다. 빈 문자열 1건(각기둥)';
COMMENT ON COLUMN chat_concept.moved_from_grade IS '2022 개정으로 학년이 이동한 개념. 현재 4건(평균/대푯값/중앙값/최빈값)이 중3에서 중1로 내려왔다';


-- 선수관계 엣지. concept_id 를 배우려면 prereq_concept_id 를 먼저 알아야 한다.
CREATE TABLE chat_concept_prereq
(
    id                BIGSERIAL PRIMARY KEY,
    concept_id        BIGINT    NOT NULL,
    prereq_concept_id BIGINT    NOT NULL,
    CONSTRAINT fk_chat_concept_prereq_concept
        FOREIGN KEY (concept_id) REFERENCES chat_concept (id),
    CONSTRAINT fk_chat_concept_prereq_prereq
        FOREIGN KEY (prereq_concept_id) REFERENCES chat_concept (id),
    CONSTRAINT uk_chat_concept_prereq UNIQUE (concept_id, prereq_concept_id),
    CONSTRAINT ck_chat_concept_prereq_no_self CHECK (concept_id <> prereq_concept_id)
);

CREATE INDEX idx_chat_concept_prereq_concept ON chat_concept_prereq (concept_id);
CREATE INDEX idx_chat_concept_prereq_prereq  ON chat_concept_prereq (prereq_concept_id);

COMMENT ON TABLE chat_concept_prereq IS '선수관계 1,098행. 방향은 concept_id -> prereq_concept_id (나중 -> 먼저)';
COMMENT ON COLUMN chat_concept_prereq.prereq_concept_id IS '상호참조(A->B 와 B->A 동시 존재) 21쌍이 원천에 있다. 원천을 훼손하지 않고 그대로 두며, 조회 쿼리의 재귀 CTE 를 UNION ALL 이 아닌 UNION 으로 써서 순환을 막는다';
