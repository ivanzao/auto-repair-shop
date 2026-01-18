CREATE TABLE order_execution_metrics (
    id              UUID PRIMARY KEY,
    order_id        UUID REFERENCES orders(id) NOT NULL UNIQUE,
    in_progress_at  TIMESTAMP NOT NULL,
    completed_at    TIMESTAMP
);
