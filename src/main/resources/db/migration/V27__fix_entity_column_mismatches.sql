-- V27: Fix entity-to-database column name mismatches
-- Coupon entity uses startsAt/expiresAt but migration V20 created valid_from/valid_until
-- Also fix usage_count vs used_count, per_customer_limit vs usage_limit_per_customer

-- Coupons table: add columns that entity expects (keep old columns for backward compat)
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS starts_at TIMESTAMP;
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS used_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS usage_limit_per_customer INTEGER;

-- Copy data from old columns to new
UPDATE coupons SET starts_at = valid_from WHERE starts_at IS NULL AND valid_from IS NOT NULL;
UPDATE coupons SET expires_at = valid_until WHERE expires_at IS NULL AND valid_until IS NOT NULL;
UPDATE coupons SET used_count = usage_count WHERE used_count = 0 AND usage_count > 0;
UPDATE coupons SET usage_limit_per_customer = per_customer_limit WHERE usage_limit_per_customer IS NULL AND per_customer_limit IS NOT NULL;

-- CMS pages: is_published exists in V20, entity uses is_active (added in V26)
-- No additional action needed - V26 already added is_active

-- Newsletter subscriptions: check if is_active exists
-- V20 created newsletter_subscriptions with is_active - should be fine
