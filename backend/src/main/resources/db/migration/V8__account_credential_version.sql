ALTER TABLE account
    ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;
