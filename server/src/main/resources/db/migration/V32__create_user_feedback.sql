CREATE TABLE user_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    content VARCHAR(2000) NOT NULL,
    contact VARCHAR(128) NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL DEFAULT 'PENDING',
    reply VARCHAR(2000) NULL,
    replied_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_feedback_user (user_id, id),
    KEY idx_user_feedback_status (status, id),
    CONSTRAINT fk_user_feedback_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT ck_user_feedback_type CHECK (type IN ('BUG', 'SUGGESTION', 'OTHER')),
    CONSTRAINT ck_user_feedback_status CHECK (status IN ('PENDING', 'PROCESSING', 'RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
