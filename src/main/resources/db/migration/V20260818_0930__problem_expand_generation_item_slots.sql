ALTER TABLE problem_generation_item ADD COLUMN slot_source VARCHAR(20);
ALTER TABLE problem_generation_item ADD COLUMN source_question_id BIGINT;
UPDATE problem_generation_item SET slot_source = 'AI_GENERATION';
ALTER TABLE problem_generation_item ALTER COLUMN slot_source SET NOT NULL;
ALTER TABLE problem_generation_item ALTER COLUMN generation_purpose DROP NOT NULL;
ALTER TABLE problem_generation_item ALTER COLUMN generation_command DROP NOT NULL;
ALTER TABLE problem_generation_item ADD CONSTRAINT fk_problem_generation_item_source_question
    FOREIGN KEY (source_question_id) REFERENCES problem_question(id);
ALTER TABLE problem_generation_item ADD CONSTRAINT ck_problem_generation_item_slot_source
    CHECK (slot_source IN ('BANK_REUSE', 'AI_GENERATION'));
ALTER TABLE problem_generation_item ADD CONSTRAINT ck_problem_generation_item_slot_payload
    CHECK ((slot_source = 'BANK_REUSE' AND source_question_id IS NOT NULL
            AND generation_purpose IS NULL AND generation_command IS NULL AND status = 'SUCCEEDED')
        OR (slot_source = 'AI_GENERATION' AND source_question_id IS NULL
            AND generation_purpose IS NOT NULL AND generation_command IS NOT NULL));
CREATE INDEX idx_problem_generation_item_source_question ON problem_generation_item(source_question_id);
