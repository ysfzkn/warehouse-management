-- valid_until should also be nullable
ALTER TABLE coupons ALTER COLUMN valid_until DROP NOT NULL;
