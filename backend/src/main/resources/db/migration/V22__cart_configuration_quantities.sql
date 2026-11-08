ALTER TABLE cart_configuration_item
    ADD COLUMN quantity TINYINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_cart_configuration_item_quantity CHECK (quantity BETWEEN 1 AND 4);
