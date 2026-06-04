-- Backfill order_number on stock events created before V40.
-- Prior code embedded the order number inside source_detail as
-- "Sipariş #ORD-YYYYMMDD-XXXXXX ...". Extract it via regex so existing
-- rows also get the clickable order link.
UPDATE stock_events
   SET order_number = substring(source_detail FROM 'Sipariş\s*#(ORD-[0-9]{8}-[A-Z0-9]+)')
 WHERE order_number IS NULL
   AND source = 'ORDER'
   AND source_detail ~ 'Sipariş\s*#ORD-[0-9]{8}-[A-Z0-9]+';
