-- Separate status-edit revision from the private credential version used to revoke sessions.
ALTER TABLE account ADD COLUMN administration_revision BIGINT NOT NULL DEFAULT 0;
CREATE INDEX ix_account_role_status_id ON account(role, status, id);
