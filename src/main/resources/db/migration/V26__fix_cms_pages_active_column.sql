-- V26: Fix cms_pages table - add is_active column (entity uses 'active' field mapped to 'is_active')
-- The original V20 created 'is_published' but entity uses 'is_active'

-- Add the is_active column that the entity expects
ALTER TABLE cms_pages ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Copy published state to active state
UPDATE cms_pages SET is_active = is_published WHERE is_published IS NOT NULL;
