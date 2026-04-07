CREATE TABLE IF NOT EXISTS contact_messages (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(150)  NOT NULL,
    email        VARCHAR(200)  NOT NULL,
    phone        VARCHAR(40),
    subject      VARCHAR(200)  NOT NULL,
    message      TEXT          NOT NULL,
    ip_address   VARCHAR(64),
    user_agent   VARCHAR(500),
    status       VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    email_sent   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at      TIMESTAMP,
    read_by      VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_contact_messages_created_at ON contact_messages(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_contact_messages_status ON contact_messages(status);
