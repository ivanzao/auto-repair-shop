CREATE INDEX idx_commands_status_created_at ON commands(status, created_at);

CREATE INDEX idx_events_status_created_at ON events(status, created_at);

CREATE INDEX idx_events_type_status_modified_at ON events(type, status, modified_at);

CREATE INDEX IF NOT EXISTS idx_processed_events_lookup ON processed_events(event_id, consumer_id);
