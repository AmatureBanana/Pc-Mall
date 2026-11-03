-- Read immutable quantities together with state under one current header lock.
-- Keep normalized items and their foreign keys; existing reservations retain all data.
ALTER TABLE stock_reservations
    ADD COLUMN quantities JSON NULL,
    ADD COLUMN creation_token CHAR(36) NOT NULL DEFAULT '';
UPDATE stock_reservations r SET quantities = (
    SELECT JSON_OBJECTAGG(CAST(i.product_id AS CHAR), i.quantity)
    FROM stock_reservation_items i WHERE i.reference = r.reference
), creation_token = UUID();
ALTER TABLE stock_reservations MODIFY COLUMN quantities JSON NOT NULL;
