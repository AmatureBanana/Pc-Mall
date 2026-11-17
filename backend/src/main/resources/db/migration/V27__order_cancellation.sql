ALTER TABLE mall_order
    ADD COLUMN cancelled_at DATETIME(6) NULL,
    ADD COLUMN cancellation_reason VARCHAR(16) NULL,
    ADD CONSTRAINT ck_order_cancellation CHECK (
        (cancelled_at IS NULL AND cancellation_reason IS NULL) OR
        (status='CANCELLED' AND cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL
            AND cancellation_reason IN ('USER','TIMEOUT'))
    ),
    ADD INDEX ix_order_timeout (status,payment_deadline,order_number);
