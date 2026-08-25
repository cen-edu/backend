-- 저장된 학습지 문항을 다시 수정할 때 연 작성 Session을 문항에 연결한다.
--
-- 이 연결이 없으면 (1) 수정 결과를 반영할 때 그 Session이 정말 이 문항에서 열린 것인지
-- 확인할 수 없고, (2) 문제은행 조회 교체가 같은 학습지에 이미 들어 있는 문항을 후보로
-- 뽑아 와서 uk_worksheet_item_question 위반으로 뒤늦게 실패한다.
--
-- 컬럼을 worksheet_item 쪽에 두는 이유는 참조 방향을 worksheet -> problem 으로 유지하기
-- 위해서다. worksheet_item 은 이미 problem_question 을 참조하고 있고, 반대 방향
-- (problem_authoring_session -> worksheet_item) 참조를 만들면 두 도메인이 서로를 가리키게 된다.
ALTER TABLE worksheet_item
    ADD COLUMN editing_session_id BIGINT,
    ADD CONSTRAINT fk_worksheet_item_editing_session
        FOREIGN KEY (editing_session_id) REFERENCES problem_authoring_session(id),
    -- 한 Session 은 최대 한 문항만 수정한다. NULL 은 여러 행이 가질 수 있으므로
    -- 수정 중이 아닌 문항에는 제약이 걸리지 않는다.
    ADD CONSTRAINT uk_worksheet_item_editing_session UNIQUE (editing_session_id);
