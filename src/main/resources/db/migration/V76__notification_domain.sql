-- Split notifications into workspace domains: WMS (warehouse ops) vs ECOM (e-commerce).
ALTER TABLE notifications ADD COLUMN domain VARCHAR(10) NOT NULL DEFAULT 'WMS';

-- Backfill existing e-commerce notifications by their entity type.
UPDATE notifications SET domain = 'ECOM'
 WHERE entity_type IN ('RETURN_REQUEST', 'ORDER', 'PAYMENT', 'COUPON', 'CUSTOMER',
                       'REVIEW', 'SUPPORT_TICKET', 'CONTACT_MESSAGE');

CREATE INDEX idx_notifications_domain ON notifications(domain);
CREATE INDEX idx_notifications_domain_read ON notifications(domain, is_read);
