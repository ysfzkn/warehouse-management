-- V30: Fix reviews.rating column type (SMALLINT -> INTEGER for Hibernate compatibility)
ALTER TABLE reviews ALTER COLUMN rating TYPE INTEGER;
