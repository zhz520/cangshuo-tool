CREATE TABLE user_sync_state (
    user_id BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_sync_state_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE user_sync_entity (
    user_id BIGINT NOT NULL,
    entity_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entity_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    device_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    deleted BOOLEAN NOT NULL,
    payload_json VARCHAR(2048) NOT NULL,
    revision BIGINT NOT NULL,
    PRIMARY KEY (user_id, entity_type, entity_key),
    UNIQUE KEY uk_sync_revision (user_id, revision),
    CONSTRAINT fk_sync_entity_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT ck_sync_revision CHECK (revision > 0),
    CONSTRAINT ck_sync_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
