-- =================================================================
-- V19: Payment transactions with idempotency key
-- Rollback: DROP TABLE payment_transactions;
-- =================================================================

CREATE TABLE payment_transactions (
    id                      BIGSERIAL PRIMARY KEY,
    order_id                BIGINT NOT NULL REFERENCES orders(id),
    idempotency_key         VARCHAR(200) NOT NULL,
    payment_provider        VARCHAR(30) NOT NULL,
    provider_payment_id     VARCHAR(200),
    conversation_id         VARCHAR(200),
    basket_id               VARCHAR(200),

    status                  VARCHAR(30) NOT NULL DEFAULT 'INITIATED',

    amount                  NUMERIC(12, 2) NOT NULL,
    paid_amount             NUMERIC(12, 2),
    currency                VARCHAR(3) NOT NULL DEFAULT 'TRY',

    installment_count       INTEGER NOT NULL DEFAULT 1,
    installment_price       NUMERIC(12, 2),

    card_last_four          VARCHAR(4),
    card_type               VARCHAR(20),
    card_association        VARCHAR(20),
    card_family             VARCHAR(50),
    card_bank_name          VARCHAR(100),

    three_d_secure          BOOLEAN NOT NULL DEFAULT FALSE,

    error_code              VARCHAR(50),
    error_message           VARCHAR(500),
    error_group             VARCHAR(50),

    paid_at                 TIMESTAMP,
    refunded_at             TIMESTAMP,
    refund_amount           NUMERIC(12, 2),

    raw_request             JSONB,
    raw_response            JSONB,
    raw_callback            JSONB,

    ip_address              VARCHAR(45),

    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_payment_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_payment_provider CHECK (payment_provider IN ('IYZICO', 'DOOR_PAYMENT')),
    CONSTRAINT chk_payment_status CHECK (status IN (
        'INITIATED', 'PROCESSING', 'SUCCESS', 'FAILED',
        'REFUND_PENDING', 'REFUNDED', 'PARTIAL_REFUNDED'
    )),
    CONSTRAINT chk_payment_currency CHECK (currency IN ('TRY', 'USD', 'EUR')),
    CONSTRAINT chk_payment_amount CHECK (amount > 0)
);

CREATE INDEX idx_payments_order ON payment_transactions (order_id);
CREATE INDEX idx_payments_status ON payment_transactions (status);
CREATE INDEX idx_payments_idempotency ON payment_transactions (idempotency_key);
CREATE INDEX idx_payments_provider_id ON payment_transactions (provider_payment_id) WHERE provider_payment_id IS NOT NULL;
CREATE INDEX idx_payments_conversation ON payment_transactions (conversation_id) WHERE conversation_id IS NOT NULL;
CREATE INDEX idx_payments_created ON payment_transactions (created_at DESC);
