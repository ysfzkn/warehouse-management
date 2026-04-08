-- V23: Add banner support to CMS pages
-- Rollback: ALTER TABLE cms_pages DROP COLUMN banner_image_url, banner_link, banner_position, banner_start, banner_end;

ALTER TABLE cms_pages ADD COLUMN banner_image_url VARCHAR(500);
ALTER TABLE cms_pages ADD COLUMN banner_link VARCHAR(500);
ALTER TABLE cms_pages ADD COLUMN banner_position VARCHAR(30);
ALTER TABLE cms_pages ADD COLUMN banner_start TIMESTAMP;
ALTER TABLE cms_pages ADD COLUMN banner_end TIMESTAMP;

-- Update page_type check constraint to include BANNER
ALTER TABLE cms_pages DROP CONSTRAINT IF EXISTS chk_cms_type;
ALTER TABLE cms_pages ADD CONSTRAINT chk_cms_type CHECK (page_type IN ('CONTENT', 'LEGAL', 'FAQ', 'BANNER'));

CREATE INDEX idx_cms_pages_position ON cms_pages (banner_position) WHERE banner_position IS NOT NULL;
CREATE INDEX idx_cms_pages_type ON cms_pages (page_type);
