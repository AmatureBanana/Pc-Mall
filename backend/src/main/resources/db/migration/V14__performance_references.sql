CREATE TABLE product_performance (
    product_id BIGINT PRIMARY KEY,
    reference_data JSON NOT NULL,
    identity_data JSON NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_performance_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_performance_reference CHECK (JSON_TYPE(reference_data) = 'OBJECT'),
    CONSTRAINT ck_performance_identity CHECK (JSON_TYPE(identity_data) = 'OBJECT')
);
