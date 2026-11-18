-- Stable numeric identity for the shared administration audit; order numbers remain the public key.
ALTER TABLE mall_order
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
    ADD COLUMN completed_at DATETIME(6) NULL,
    ADD COLUMN completed_by VARCHAR(20) NULL,
    ADD INDEX ix_order_admin_created (created_at,order_number);
