CREATE TABLE processed_events (
    event_id UUID NOT NULL,
    consumer_id VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (event_id, consumer_id)
);
