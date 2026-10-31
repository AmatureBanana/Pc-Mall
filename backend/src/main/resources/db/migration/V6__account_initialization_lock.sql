-- Serializes the first administrator initialization across concurrent starts.
CREATE TABLE account_initialization_lock (id TINYINT NOT NULL PRIMARY KEY);
INSERT INTO account_initialization_lock (id) VALUES (1);
