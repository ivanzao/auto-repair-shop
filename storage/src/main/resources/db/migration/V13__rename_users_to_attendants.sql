-- HML/PROD atualmente sem dados: migration assume tabelas vazias.

-- Drop FK em orders → users (nome convencional do Postgres)
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_attendant_id_fkey;

-- App não é mais dono de users (Lambda admin terá migration própria)
DROP TABLE IF EXISTS users CASCADE;

-- Cria attendants enxuto (sem hashed_password, sem role)
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

-- Restaura FK orders → attendants
ALTER TABLE orders
    ADD CONSTRAINT orders_attendant_id_fkey
    FOREIGN KEY (attendant_id) REFERENCES attendants(id);
