DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'stock_transfers'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'stock_transfers'
          AND column_name = 'driver_tc_id'
    ) THEN
        ALTER TABLE stock_transfers
            ALTER COLUMN driver_tc_id TYPE VARCHAR(11);
    END IF;
END
$$;

