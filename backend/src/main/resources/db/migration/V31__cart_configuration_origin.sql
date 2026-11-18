ALTER TABLE cart_configuration
  ADD COLUMN origin VARCHAR(14) NOT NULL DEFAULT 'DIY',
  ADD CONSTRAINT chk_cart_configuration_origin CHECK (origin IN ('DIY', 'RECOMMENDATION'));
