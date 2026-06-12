-- AI-generated set cover support.
-- ai_role: NULL = normal image; 'COVER_INPUT' = per-member input photo selected for
-- AI cover generation (hidden from gallery/store); 'AI_COVER' = the AI-generated
-- cover image (behaves as a normal gallery image, marked for the admin UI only).
-- member_product_id: for COVER_INPUT rows, which bundle member the photo belongs to.
ALTER TABLE product_images ADD COLUMN ai_role VARCHAR(20) NULL;
ALTER TABLE product_images ADD COLUMN member_product_id BIGINT NULL;
ALTER TABLE product_images ADD CONSTRAINT fk_product_images_member_product
    FOREIGN KEY (member_product_id) REFERENCES products(id) ON DELETE SET NULL;
CREATE INDEX idx_product_images_product_ai_role ON product_images(product_id, ai_role);
