-- Flyway migration: create users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    modified_at TIMESTAMP NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    contact VARCHAR(255) NOT NULL,
    document VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    CONSTRAINT users_document_unique UNIQUE (document)
);
