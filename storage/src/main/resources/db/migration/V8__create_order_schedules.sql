CREATE TABLE order_schedules
(
    id                UUID PRIMARY KEY,
    created_at        TIMESTAMP                   NOT NULL,
    modified_at       TIMESTAMP                   NOT NULL,

    order_id          UUID REFERENCES orders (id) NOT NULL,
    schedule_datetime TIMESTAMP                   NOT NULL,
    "type"            VARCHAR(255)                NOT NULL
)