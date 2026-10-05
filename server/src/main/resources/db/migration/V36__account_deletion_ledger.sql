-- No email, password, token or content is retained. Used to suppress deleted accounts after backup restore.
CREATE TABLE deleted_account (
    user_id BIGINT NOT NULL PRIMARY KEY,
    deleted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;
