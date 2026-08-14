ALTER TABLE worksheet_gen_spec ADD COLUMN question_type VARCHAR(20);
UPDATE worksheet_gen_spec SET question_type = 'STEP_FILL' WHERE question_type IS NULL;
ALTER TABLE worksheet_gen_spec ALTER COLUMN question_type SET NOT NULL;

ALTER TABLE worksheet_gen_spec ADD CONSTRAINT ck_worksheet_gen_spec_question_type
    CHECK (question_type IN ('MULTIPLE_CHOICE', 'SHORT_INPUT', 'STEP_FILL', 'ESSAY'));

ALTER TABLE worksheet_gen_spec DROP CONSTRAINT uk_worksheet_gen_spec;
ALTER TABLE worksheet_gen_spec ADD CONSTRAINT uk_worksheet_gen_spec
    UNIQUE (worksheet_id, sub_unit_id, question_type, difficulty);

COMMENT ON COLUMN worksheet_gen_spec.question_type IS
    '출제 조건의 유형 축. 일반 학습은 STEP_FILL 고정. 종합평가는 같은 소단원·난이도에 유형이 갈리므로 유니크 키에 포함';
