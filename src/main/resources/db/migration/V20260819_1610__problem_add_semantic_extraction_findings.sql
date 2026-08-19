ALTER TABLE problem_question
    ADD COLUMN IF NOT EXISTS semantic_extraction_findings JSONB;
