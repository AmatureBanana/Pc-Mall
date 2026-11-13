-- Keep group identity and amount after the source cart group is removed.
CREATE TABLE order_configuration (
    order_number VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    configuration_id BIGINT NOT NULL,
    amount DECIMAL(20,2) NOT NULL,
    PRIMARY KEY (order_number, configuration_id),
    CONSTRAINT fk_order_configuration_order FOREIGN KEY (order_number) REFERENCES mall_order(order_number) ON DELETE CASCADE,
    CONSTRAINT ck_order_configuration_amount CHECK (amount > 0)
);

ALTER TABLE order_item ADD CONSTRAINT fk_order_item_configuration
    FOREIGN KEY (order_number, configuration_id) REFERENCES order_configuration(order_number, configuration_id);
