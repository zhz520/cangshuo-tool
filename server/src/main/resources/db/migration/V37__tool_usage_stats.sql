CREATE TABLE tool_usage_stat (
    tool_code VARCHAR(64) COLLATE utf8mb4_0900_as_cs NOT NULL,
    use_count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (tool_code),
    CONSTRAINT fk_tool_usage_tool FOREIGN KEY (tool_code) REFERENCES tool_definition (tool_code),
    CONSTRAINT chk_tool_usage_count CHECK (use_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
