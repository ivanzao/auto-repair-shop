CREATE TABLE services
(
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMP      NOT NULL,
    modified_at TIMESTAMP      NOT NULL,
    version     INT            NOT NULL DEFAULT 0,
    name        VARCHAR(255)   NOT NULL,
    description TEXT,
    price       DECIMAL(10, 2) NOT NULL
);

CREATE TABLE service_supplies
(
    service_id  UUID NOT NULL REFERENCES services (id),
    supply_id UUID NOT NULL REFERENCES supplies (id),
    quantity  INT  NOT NULL,
    PRIMARY KEY (service_id, supply_id)
);