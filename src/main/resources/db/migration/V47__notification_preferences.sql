-- Müşteri bildirim tercihleri tablosu
CREATE TABLE notification_preferences (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    channel      VARCHAR(20) NOT NULL,       -- EMAIL, SMS, PUSH
    type         VARCHAR(30) NOT NULL,       -- ORDER_CONFIRMED, CARGO_SHIPPED, MARKETING vb.
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uniq_customer_channel_type UNIQUE (customer_id, channel, type)
);

CREATE INDEX idx_notification_preferences_customer ON notification_preferences(customer_id);

-- SMS site_settings
INSERT INTO site_settings (setting_key, setting_value, setting_type) VALUES
    ('sms_enabled', 'false', 'BOOLEAN'),
    ('sms_provider', 'MOCK', 'STRING'),
    ('netgsm_username', '', 'STRING'),
    ('netgsm_password', '', 'STRING'),
    ('netgsm_sender', '', 'STRING')
ON CONFLICT (setting_key) DO NOTHING;
