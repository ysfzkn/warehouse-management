-- Allow a bundle member to be marked as a gift: it ships with the set (real stock)
-- but is excluded from the set's price total and shown with a "Hediye" badge.
ALTER TABLE bundle_items ADD COLUMN is_gift BOOLEAN NOT NULL DEFAULT FALSE;
