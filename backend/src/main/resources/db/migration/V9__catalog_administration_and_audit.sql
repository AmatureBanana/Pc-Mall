ALTER TABLE products ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

-- Shared audit storage for subsequent administration services; no authentication secrets.
CREATE TABLE admin_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id BIGINT NOT NULL,
    actor_username VARCHAR(20) NOT NULL,
    object_type VARCHAR(40) NOT NULL,
    object_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    before_value JSON NOT NULL,
    after_value JSON NOT NULL,
    INDEX ix_audit_object (object_type, object_id, id)
);
