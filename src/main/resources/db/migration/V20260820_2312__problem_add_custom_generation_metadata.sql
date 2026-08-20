ALTER TABLE problem_generation_item ADD COLUMN custom_stage VARCHAR(20);
ALTER TABLE problem_generation_item ADD COLUMN origin_question_id BIGINT;

ALTER TABLE problem_generation_item ADD CONSTRAINT ck_problem_generation_item_custom_stage
    CHECK (custom_stage IS NULL OR custom_stage IN ('REVIEW', 'SIMILAR', 'ADVANCED'));

ALTER TABLE problem_generation_item ADD CONSTRAINT fk_problem_generation_item_origin_question
    FOREIGN KEY (origin_question_id) REFERENCES problem_question(id);

CREATE INDEX idx_problem_generation_item_origin_question
    ON problem_generation_item(origin_question_id);
