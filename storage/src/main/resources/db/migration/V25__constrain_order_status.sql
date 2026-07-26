ALTER TABLE orders
    ADD CONSTRAINT orders_status_check
    CHECK (status IN (
        'RECEIVED',
        'WAITING_APPROVAL',
        'EXECUTION_ENQUEUED',
        'IN_PROGRESS',
        'COMPLETED',
        'DELIVERED',
        'CANCELED'
    ));
