CREATE TABLE admin_security_codes (
    id BIGSERIAL PRIMARY KEY,
    code_hash VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100)
);

