CREATE TABLE mall_order (
    order_number VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    account_id BIGINT NOT NULL,
    reservation_reference VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    total DECIMAL(20,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    payment_deadline DATETIME(6) NOT NULL,
    CONSTRAINT fk_order_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_order_reservation FOREIGN KEY (reservation_reference) REFERENCES stock_reservations(reference),
    CONSTRAINT ck_order_status CHECK (status IN ('PENDING_PAYMENT','PAID','CANCELLED','COMPLETED')),
    CONSTRAINT ck_order_money CHECK (currency='CNY' AND total > 0),
    CONSTRAINT ck_order_deadline CHECK (payment_deadline > created_at),
    INDEX ix_order_owner_created (account_id,created_at)
);

-- Historical values are inserted at checkout; catalog and cart writes never update them.
CREATE TABLE order_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    product_id BIGINT NOT NULL,
    sku VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    model VARCHAR(100) NOT NULL,
    category VARCHAR(16) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    amount DECIMAL(20,2) NOT NULL,
    configuration_id BIGINT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_number) REFERENCES mall_order(order_number) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_order_item_money CHECK (price > 0 AND amount=price*quantity),
    CONSTRAINT ck_order_item_quantity CHECK (quantity BETWEEN 1 AND 99)
);

CREATE TABLE order_request (
    account_id BIGINT NOT NULL,
    operation VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_number VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (account_id,operation,request_key),
    CONSTRAINT fk_order_request_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_order_request_order FOREIGN KEY (order_number) REFERENCES mall_order(order_number)
);
