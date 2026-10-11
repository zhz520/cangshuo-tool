-- Existing cumulative opens have no date information: do not invent historical rows.
CREATE TABLE tool_usage_daily (
    usage_date DATE NOT NULL,
    tool_code VARCHAR(64) COLLATE utf8mb4_0900_as_cs NOT NULL,
    use_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (usage_date, tool_code),
    KEY idx_usage_daily_tool (tool_code, usage_date),
    CONSTRAINT fk_usage_daily_tool FOREIGN KEY (tool_code) REFERENCES tool_definition(tool_code),
    CONSTRAINT chk_usage_daily_count CHECK (use_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_activity_daily (
    user_id BIGINT NOT NULL,
    activity_date DATE NOT NULL,
    PRIMARY KEY (user_id, activity_date),
    KEY idx_activity_date (activity_date, user_id),
    CONSTRAINT fk_activity_daily_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE analytics_collection (
    id TINYINT NOT NULL PRIMARY KEY,
    started_at DATETIME(3) NOT NULL,
    CONSTRAINT chk_analytics_singleton CHECK (id = 1)
) ENGINE=InnoDB;
INSERT INTO analytics_collection (id, started_at) VALUES (1, UTC_TIMESTAMP(3));

CREATE INDEX idx_user_created ON sys_user(created_at);
CREATE INDEX idx_feedback_created ON user_feedback(created_at);
CREATE INDEX idx_site_created ON site_link(created_at);
CREATE INDEX idx_ai_usage_date ON ai_daily_usage(usage_date);
