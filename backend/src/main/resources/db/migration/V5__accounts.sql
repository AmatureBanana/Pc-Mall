CREATE TABLE account (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(20) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    password_hash CHAR(60) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nickname VARCHAR(30) NOT NULL,
    role VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_account_username UNIQUE (username),
    CONSTRAINT ck_account_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
