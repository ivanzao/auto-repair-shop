CREATE TABLE order_approval_tokens (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    modified_at TIMESTAMP NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    order_id UUID NOT NULL REFERENCES orders(id),
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP
);

CREATE INDEX idx_order_approval_tokens_order_id ON order_approval_tokens(order_id);
CREATE INDEX idx_order_approval_tokens_expires_at ON order_approval_tokens(expires_at);