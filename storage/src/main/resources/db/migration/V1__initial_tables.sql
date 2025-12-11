CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    modified_at TIMESTAMP NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    hashed_password VARCHAR(255) NOT NULL,
    contact VARCHAR(255) NOT NULL,
    document VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    CONSTRAINT users_document_unique UNIQUE (document)
);

CREATE TABLE IF NOT EXISTS supplies (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    modified_at TIMESTAMP NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) ,
    quantity INTEGER NOT NULL,
    price NUMERIC(10, 2) NOT NULL
);

CREATE TABLE customers (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    modified_at TIMESTAMP NOT NULL,
    version INT NOT NULL DEFAULT 0,
    name VARCHAR(255) NOT NULL,
    document VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    contact VARCHAR(255) NOT NULL
);

CREATE TABLE vehicles (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP NOT NULL,
  modified_at TIMESTAMP NOT NULL,
  version INT NOT NULL DEFAULT 0,
  client_id UUID NOT NULL REFERENCES customers(id),
  plate VARCHAR(20) NOT NULL UNIQUE,
  brand VARCHAR(100) NOT NULL,
  model VARCHAR(100) NOT NULL,
  year INT NOT NULL
);
