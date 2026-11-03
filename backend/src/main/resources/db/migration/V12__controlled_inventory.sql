-- stock is immediately available for sale; reserved units are accounted for separately.
ALTER TABLE products
    ADD COLUMN reserved_stock INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_reserved_stock CHECK (reserved_stock >= 0 AND stock + reserved_stock <= 2147483647);

CREATE TABLE stock_reservations (
    reference VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    state VARCHAR(16) NOT NULL,
    CONSTRAINT ck_reservation_state CHECK (state IN ('RESERVED', 'RELEASED'))
);
CREATE TABLE stock_reservation_items (
    reference VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (reference, product_id),
    CONSTRAINT fk_stock_reference FOREIGN KEY (reference) REFERENCES stock_reservations(reference),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_reserved_quantity CHECK (quantity > 0)
);
