CREATE TABLE orders (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    modified_at TIMESTAMP NOT NULL,
    version INT NOT NULL DEFAULT 0,
    customer_id UUID NOT NULL REFERENCES customers(id),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
    attendant_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    technician VARCHAR(255)
);

CREATE TABLE order_services (
    order_id UUID NOT NULL REFERENCES orders(id),
    service_id UUID NOT NULL REFERENCES services(id),
    PRIMARY KEY (order_id, service_id)
);

CREATE TABLE order_supplies (
    order_id UUID NOT NULL REFERENCES orders(id),
    supply_id UUID NOT NULL REFERENCES supplies(id),
    quantity INT NOT NULL,
    PRIMARY KEY (order_id, supply_id)
);
