DROP INDEX IF EXISTS idx_events_status_created_at;
DROP INDEX IF EXISTS idx_events_type_status_modified_at;

ALTER TABLE events DROP COLUMN IF EXISTS status;

CREATE INDEX IF NOT EXISTS idx_events_created_at ON events (created_at);
