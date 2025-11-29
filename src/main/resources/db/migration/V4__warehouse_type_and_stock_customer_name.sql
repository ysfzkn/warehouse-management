-- Add warehouse_type column to warehouses table
ALTER TABLE warehouses ADD COLUMN warehouse_type VARCHAR(20) NOT NULL DEFAULT 'STANDART';

-- Update existing warehouses to STANDART
UPDATE warehouses SET warehouse_type = 'STANDART' WHERE warehouse_type IS NULL OR warehouse_type = '';

-- Add customer_name column to stocks table
ALTER TABLE stocks ADD COLUMN customer_name VARCHAR(255);

-- Create index for customer_name
CREATE INDEX idx_stocks_customer_name ON stocks (customer_name);

-- Drop the old unique constraint on (product_id, warehouse_id)
-- Note: We'll handle uniqueness in the application layer based on warehouse type
ALTER TABLE stocks DROP CONSTRAINT IF EXISTS uk_stocks_product_warehouse;

