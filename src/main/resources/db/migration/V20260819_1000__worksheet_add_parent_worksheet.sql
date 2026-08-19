-- 맞춤 학습의 계보. 차수(1차/2차)를 저장하지 않고 이 체인의 깊이로 파생한다.
--
-- source_assignment_id 와 역할이 다르다. 그쪽은 묶음 키(원본 배정)라 2차 맞춤도 원본을 가리키고,
-- 이쪽은 직전 차수를 가리킨다. 한 컬럼이 둘을 겸하면 2차가 생기는 순간 묶음 조회와 계보가 충돌한다.
ALTER TABLE worksheet
    ADD COLUMN parent_worksheet_id BIGINT;

-- 기존 맞춤 학습지를 1차로 채운다. 지금까지 2차를 만드는 경로가 없어 전부 1차이고, 1차의 부모는
-- 정의상 출처 배정의 학습지다. 이 백필을 빼면 기존 맞춤이 전부 계보가 끊겨 화면에서 사라진다.
-- 제약보다 먼저 돌려서, 어긋난 행이 있으면 아래 CHECK 가 마이그레이션을 실패시키게 한다.
UPDATE worksheet w
   SET parent_worksheet_id = a.worksheet_id
  FROM worksheet_assignment a
 WHERE w.source_assignment_id = a.id
   AND w.origin = 'CUSTOM'
   AND w.parent_worksheet_id IS NULL;

ALTER TABLE worksheet
    ADD CONSTRAINT fk_worksheet_parent
        FOREIGN KEY (parent_worksheet_id) REFERENCES worksheet (id),
    -- 부모는 반드시 먼저 존재하므로 id가 항상 작다. 순환 참조를 물리적으로 막는다.
    ADD CONSTRAINT ck_worksheet_parent_precedes
        CHECK (parent_worksheet_id IS NULL OR parent_worksheet_id < id);

-- PostgreSQL은 FK 인덱스를 자동 생성하지 않는다.
CREATE INDEX idx_worksheet_parent ON worksheet (parent_worksheet_id);

COMMENT ON COLUMN worksheet.parent_worksheet_id IS
    'CUSTOM에서만 값. 직전 차수의 학습지(1차는 원본 학습지, 2차는 1차 맞춤). 차수는 이 체인의 깊이다. source_assignment_id는 묶음 키(원본 배정)이고 모든 차수가 원본을 가리킨다';
