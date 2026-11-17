CREATE TABLE account_avatar (
    account_id BIGINT PRIMARY KEY,
    image MEDIUMBLOB NOT NULL,
    FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE
);
