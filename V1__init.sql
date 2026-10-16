CREATE TABLE payments (
    id                UUID PRIMARY KEY,
    idempotency_key   VARCHAR(100) NOT NULL UNIQUE,
    customer_id       VARCHAR(100) NOT NULL,
    amount_paise      BIGINT       NOT NULL,
    currency          VARCHAR(3)   NOT NULL,
    status            VARCHAR(30)  NOT NULL,
    provider_order_id VARCHAR(100),
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_payments_status_updated ON payments (status, updated_at);
CREATE INDEX idx_payments_provider_order ON payments (provider_order_id);

CREATE TABLE processed_events (
    event_id     VARCHAR(100) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
