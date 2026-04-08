-- valid_from should be nullable — coupons can be created without a start date (immediately active)
ALTER TABLE coupons ALTER COLUMN valid_from DROP NOT NULL;
