-- One persistent logical cart per account. Reads never reserve or decrement stock.
CREATE TABLE shopping_cart (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_account UNIQUE (account_id),
    CONSTRAINT fk_cart_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT ck_cart_revision CHECK (revision >= 0)
);

-- A plain single item line. Same SKU merges into one row; the 1..99 bound is the per-line rule
-- and is not applied to cross-line aggregates of the same SKU by later tickets.
CREATE TABLE shopping_cart_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_item UNIQUE (cart_id, product_id),
    CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id) REFERENCES shopping_cart (id),
    CONSTRAINT fk_cart_item_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_cart_item_quantity CHECK (quantity BETWEEN 1 AND 99)
);

-- Retry protection for the one cart write that could otherwise duplicate a line. The fingerprint
-- binds the key to the account, operation and request content, so a reused key with different
-- content is rejected instead of being silently applied.
CREATE TABLE cart_request (
    account_id BIGINT NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    creation_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result JSON NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (account_id, request_key),
    CONSTRAINT fk_cart_request_account FOREIGN KEY (account_id) REFERENCES account (id)
);
