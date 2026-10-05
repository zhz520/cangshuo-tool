CREATE TABLE stored_object (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    owner_id BIGINT NOT NULL,
    object_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_type VARCHAR(128) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stored_object_key (object_key),
    KEY idx_stored_object_owner (owner_type, owner_id, deleted_at),
    CONSTRAINT ck_stored_object_owner CHECK (owner_type IN ('ADMIN', 'USER', 'SYSTEM')),
    CONSTRAINT ck_stored_object_size CHECK (size_bytes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
