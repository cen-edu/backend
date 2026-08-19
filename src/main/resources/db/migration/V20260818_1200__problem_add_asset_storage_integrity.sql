ALTER TABLE problem_asset_storage_task
    ADD COLUMN IF NOT EXISTS expected_checksum VARCHAR(64),
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(100);

UPDATE problem_asset_storage_task
SET expected_checksum = 'legacy',
    content_type = 'application/octet-stream'
WHERE expected_checksum IS NULL OR content_type IS NULL;

ALTER TABLE problem_asset_storage_task
    ALTER COLUMN expected_checksum SET NOT NULL,
    ALTER COLUMN content_type SET NOT NULL;
