-- V34: Increase product description limit from 500 to TEXT (unlimited)
ALTER TABLE products ALTER COLUMN description TYPE TEXT;
