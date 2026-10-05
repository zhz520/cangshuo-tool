CREATE TABLE admin_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    role_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    last_login_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_user_username (username),
    KEY idx_admin_user_role_status (role_code, status),
    CONSTRAINT ck_admin_user_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE admin_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_id BIGINT NULL,
    module VARCHAR(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    operation VARCHAR(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    request_uri VARCHAR(255) NOT NULL,
    request_method VARCHAR(10) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    ip VARCHAR(45) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    result VARCHAR(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_admin_operation_log_admin_created (admin_id, created_at),
    CONSTRAINT fk_admin_operation_log_admin FOREIGN KEY (admin_id) REFERENCES admin_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
