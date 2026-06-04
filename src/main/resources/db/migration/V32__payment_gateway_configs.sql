-- =====================================================
-- V32: Payment Gateway Configurations Table
-- Supports multiple POS terminals: NestPay, GVP, iyzico
-- =====================================================

CREATE TABLE payment_gateway_configs (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    gateway_protocol VARCHAR(30) NOT NULL,     -- NESTPAY, GVP, IYZICO
    bank_code VARCHAR(30),                     -- ISBANK, AKBANK, GARANTI, YAPI_KREDI, HALKBANK, TEB, DENIZBANK, ING, ZIRAAT, KUVEYT, QNB

    -- Credentials
    merchant_id VARCHAR(200),
    terminal_id VARCHAR(200),
    store_key TEXT,
    provision_password TEXT,
    api_key TEXT,
    secret_key TEXT,

    -- Endpoints
    base_url VARCHAR(500),
    three_d_url VARCHAR(500),
    callback_url VARCHAR(500),

    -- Behavior
    is_active BOOLEAN NOT NULL DEFAULT false,
    is_default BOOLEAN NOT NULL DEFAULT false,
    is_sandbox BOOLEAN NOT NULL DEFAULT true,
    priority INTEGER NOT NULL DEFAULT 100,
    supported_cards VARCHAR(200) DEFAULT 'VISA,MASTERCARD,TROY',
    max_installments INTEGER DEFAULT 12,

    -- Protocol-specific extras
    extra_config JSONB DEFAULT '{}',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_gateway_configs_active ON payment_gateway_configs(is_active);
CREATE INDEX idx_gateway_configs_protocol ON payment_gateway_configs(gateway_protocol);

-- Add gateway_config reference to payment_transactions
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS gateway_config_id BIGINT
    REFERENCES payment_gateway_configs(id);
