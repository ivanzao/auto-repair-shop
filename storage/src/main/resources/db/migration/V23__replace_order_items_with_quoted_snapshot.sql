DROP TABLE IF EXISTS order_supplies;

CREATE TABLE order_quoted_services (
    order_id   UUID           NOT NULL REFERENCES orders (id),
    service_id UUID           NOT NULL,
    name       VARCHAR(255)   NOT NULL,
    price      DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (order_id, service_id)
);

CREATE TABLE order_quoted_supplies (
    order_id   UUID           NOT NULL REFERENCES orders (id),
    supply_id  UUID           NOT NULL,
    name       VARCHAR(255)   NOT NULL,
    quantity   INT            NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (order_id, supply_id)
);
