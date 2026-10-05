CREATE TABLE announcement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(128) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    level VARCHAR(16) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL DEFAULT 'INFO',
    status TINYINT NOT NULL DEFAULT 0,
    start_at DATETIME(3) NULL,
    end_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_announcement_active (status, start_at, end_at, id),
    CONSTRAINT ck_announcement_level CHECK (level IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT ck_announcement_status CHECK (status IN (0, 1)),
    CONSTRAINT ck_announcement_window CHECK (end_at IS NULL OR start_at IS NULL OR end_at > start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
