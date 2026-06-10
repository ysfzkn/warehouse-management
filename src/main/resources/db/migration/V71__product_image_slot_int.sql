-- V70 created product_images.slot as SMALLINT, but the JPA entity maps it as
-- Integer (expects INTEGER), causing Hibernate schema-validation to fail.
-- Widen the column to INTEGER so the entity and schema agree.
ALTER TABLE product_images ALTER COLUMN slot TYPE INTEGER;
