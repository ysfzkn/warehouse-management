-- Add customer_phone column to stocks table
ALTER TABLE stocks ADD COLUMN customer_phone VARCHAR(20);

-- Create index for customer_phone
CREATE INDEX idx_stocks_customer_phone ON stocks (customer_phone);

