-- =================================================================
-- V16: Customer accounts, addresses, and refresh tokens
-- Rollback: DROP TABLE customer_refresh_tokens, customer_addresses, customers;
-- =================================================================

CREATE TABLE customers (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(120) NOT NULL,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    phone               VARCHAR(20),
    tc_kimlik_no        VARCHAR(11),
    gender              VARCHAR(10),
    birth_date          DATE,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    email_verify_token  VARCHAR(200),
    email_verify_sent_at TIMESTAMP,
    kvkk_consent        BOOLEAN NOT NULL DEFAULT FALSE,
    kvkk_consent_at     TIMESTAMP,
    marketing_consent   BOOLEAN NOT NULL DEFAULT FALSE,
    marketing_consent_at TIMESTAMP,
    password_reset_token VARCHAR(200),
    password_reset_sent_at TIMESTAMP,
    last_login_at       TIMESTAMP,
    last_login_ip       VARCHAR(45),
    failed_login_count  INTEGER NOT NULL DEFAULT 0,
    locked_until        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_customers_email UNIQUE (email),
    CONSTRAINT chk_customers_tc CHECK (tc_kimlik_no IS NULL OR LENGTH(tc_kimlik_no) = 11),
    CONSTRAINT chk_customers_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER'))
);

CREATE INDEX idx_customers_email ON customers (email);
CREATE INDEX idx_customers_phone ON customers (phone) WHERE phone IS NOT NULL;
CREATE INDEX idx_customers_active ON customers (is_active) WHERE is_active = TRUE;

CREATE TABLE customer_addresses (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    title           VARCHAR(100) NOT NULL,
    address_type    VARCHAR(20) NOT NULL DEFAULT 'SHIPPING',
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20) NOT NULL,
    city            VARCHAR(100) NOT NULL,
    district        VARCHAR(100) NOT NULL,
    neighborhood    VARCHAR(200),
    address_line    VARCHAR(500) NOT NULL,
    postal_code     VARCHAR(10),
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    tc_kimlik_no    VARCHAR(11),
    company_name    VARCHAR(200),
    tax_office      VARCHAR(200),
    tax_number      VARCHAR(20),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_address_type CHECK (address_type IN ('SHIPPING', 'BILLING', 'BOTH'))
);

CREATE INDEX idx_customer_addresses_customer ON customer_addresses (customer_id);

CREATE TABLE customer_refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    token           VARCHAR(500) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at      TIMESTAMP,

    CONSTRAINT uq_refresh_token UNIQUE (token)
);

CREATE INDEX idx_refresh_tokens_customer ON customer_refresh_tokens (customer_id);
