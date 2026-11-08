CREATE TABLE cart_configuration (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    signature VARCHAR(180) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    UNIQUE (cart_id, signature),
    FOREIGN KEY (cart_id) REFERENCES shopping_cart(id)
);

CREATE TABLE cart_configuration_item (
    configuration_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (configuration_id, product_id),
    FOREIGN KEY (configuration_id) REFERENCES cart_configuration(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id)
);
