CREATE TABLE sys_config (
    config_key VARCHAR(64) COLLATE utf8mb4_0900_as_cs NOT NULL,
    config_value VARCHAR(500) NOT NULL DEFAULT '',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_config (config_key, config_value) VALUES
    ('APP_LATEST_VERSION_CODE', '1'),
    ('APP_LATEST_VERSION_NAME', '0.1.0'),
    ('APP_UPDATE_URL', ''),
    ('APP_UPDATE_NOTES', '')
ON DUPLICATE KEY UPDATE config_key = config_key;
