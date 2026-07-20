
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_attendant_id_fkey;

DROP TABLE IF EXISTS users CASCADE;

CREATE TABLE attendants (
    id           UUID         PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    document     VARCHAR(20)  NOT NULL UNIQUE,
    email        VARCHAR(255) NOT NULL UNIQUE,
    contact      VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    modified_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    version      INTEGER      NOT NULL DEFAULT 0
);

ALTER TABLE orders
    ADD CONSTRAINT orders_attendant_id_fkey
    FOREIGN KEY (attendant_id) REFERENCES attendants(id);
