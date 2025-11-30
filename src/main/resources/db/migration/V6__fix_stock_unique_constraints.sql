-- Drop any existing unique constraints and indexes on stocks table
-- PostgreSQL may create constraints with auto-generated names (like ukt63cpr1a23ari7clpnbfglbtf)
-- So we need to find them by checking the actual columns, not just the name
DO $$
DECLARE
    constraint_record RECORD;
    index_record RECORD;
    table_oid OID;
BEGIN
    -- Get the table OID
    SELECT oid INTO table_oid FROM pg_class WHERE relname = 'stocks' AND relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = 'public');
    
    IF table_oid IS NULL THEN
        RAISE NOTICE 'stocks table not found, skipping constraint/index cleanup';
        RETURN;
    END IF;
    
    -- Drop all unique constraints on stocks table (regardless of name)
    -- Check both table_constraints and pg_constraint for completeness
    FOR constraint_record IN (
        SELECT conname, conrelid
        FROM pg_constraint
        WHERE conrelid = table_oid
          AND contype = 'u'
    ) LOOP
        BEGIN
            EXECUTE 'ALTER TABLE stocks DROP CONSTRAINT IF EXISTS ' || quote_ident(constraint_record.conname) || ' CASCADE';
            RAISE NOTICE 'Dropped constraint: %', constraint_record.conname;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE NOTICE 'Could not drop constraint %: %', constraint_record.conname, SQLERRM;
        END;
    END LOOP;
    
    -- Drop all unique indexes on stocks table that involve product_id and warehouse_id
    -- This catches cases where PostgreSQL created unique indexes instead of constraints
    FOR index_record IN (
        SELECT i.indexname, i.indexdef
        FROM pg_indexes i
        JOIN pg_class c ON c.relname = i.indexname
        JOIN pg_index idx ON idx.indexrelid = c.oid
        WHERE i.schemaname = 'public'
          AND i.tablename = 'stocks'
          AND idx.indisunique = true
          AND (
              i.indexdef LIKE '%product_id%warehouse_id%' 
              OR i.indexdef LIKE '%warehouse_id%product_id%'
              OR i.indexname LIKE '%product%warehouse%'
              OR i.indexname LIKE '%stocks%unique%'
              OR i.indexname LIKE 'uk%'
          )
    ) LOOP
        BEGIN
            EXECUTE 'DROP INDEX IF EXISTS ' || quote_ident(index_record.indexname) || ' CASCADE';
            RAISE NOTICE 'Dropped index: %', index_record.indexname;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE NOTICE 'Could not drop index %: %', index_record.indexname, SQLERRM;
        END;
    END LOOP;
    
    -- Also check pg_index directly for any unique indexes on (product_id, warehouse_id)
    -- This catches the auto-generated constraint names like ukt63cpr1a23ari7clpnbfglbtf
    FOR index_record IN (
        SELECT c.relname as indexname,
               array_to_string(array_agg(a.attname ORDER BY array_position(idx.indkey, a.attnum)), ',') as columns
        FROM pg_index idx
        JOIN pg_class c ON c.oid = idx.indexrelid
        JOIN pg_class t ON t.oid = idx.indrelid
        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(idx.indkey)
        WHERE t.relname = 'stocks'
          AND idx.indisunique = true
          AND c.relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = 'public')
        GROUP BY c.relname, idx.indkey
        HAVING COUNT(DISTINCT CASE WHEN a.attname IN ('product_id', 'warehouse_id') THEN a.attname END) = 2
           AND COUNT(DISTINCT a.attname) = 2  -- Only indexes with exactly these 2 columns
    ) LOOP
        BEGIN
            EXECUTE 'DROP INDEX IF EXISTS ' || quote_ident(index_record.indexname) || ' CASCADE';
            RAISE NOTICE 'Dropped unique index on (product_id, warehouse_id): %', index_record.indexname;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE NOTICE 'Could not drop index %: %', index_record.indexname, SQLERRM;
        END;
    END LOOP;
END $$;

-- Drop any existing unique indexes that might conflict (by name)
DROP INDEX IF EXISTS uk_stocks_standart_unique CASCADE;
DROP INDEX IF EXISTS uk_stocks_emanet_unique CASCADE;
DROP INDEX IF EXISTS uk_stocks_product_warehouse CASCADE;

-- Create partial unique index for STANDART warehouses (customer_name must be NULL)
-- This ensures one stock per product per warehouse for STANDART warehouses
-- STANDART warehouses should not have customer_name
CREATE UNIQUE INDEX uk_stocks_standart_unique 
ON stocks (product_id, warehouse_id) 
WHERE customer_name IS NULL;

-- Create partial unique index for EMANET_DEPO warehouses (customer_name must be NOT NULL)
-- This allows multiple stocks per product per warehouse for different customers
-- Each customer can have one stock per product per warehouse
CREATE UNIQUE INDEX uk_stocks_emanet_unique 
ON stocks (product_id, warehouse_id, customer_name) 
WHERE customer_name IS NOT NULL;

