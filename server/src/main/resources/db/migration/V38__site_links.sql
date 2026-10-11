CREATE TABLE site_link (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    url VARCHAR(500) NOT NULL,
    description VARCHAR(300) NOT NULL DEFAULT '',
    status VARCHAR(16) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'PENDING',
    submitted_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_site_link_status (status, updated_at, id),
    CONSTRAINT fk_site_link_user FOREIGN KEY (submitted_by) REFERENCES sys_user(id) ON DELETE SET NULL,
    CONSTRAINT chk_site_link_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
