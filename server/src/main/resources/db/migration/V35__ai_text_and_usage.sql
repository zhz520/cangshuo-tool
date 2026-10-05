CREATE TABLE ai_daily_usage (
    user_id BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, usage_date),
    CONSTRAINT fk_ai_daily_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT chk_ai_attempts CHECK (attempts >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO tool_definition (tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured)
VALUES ('ai_text', 'AI 文本助手', '生成文本摘要、改写内容、进行中英翻译。',
    (SELECT id FROM tool_category WHERE code = 'AI'), 'text',
    JSON_ARRAY('AI', '摘要', '改写', '翻译', 'summarize', 'rewrite', 'translate'),
    'SERVER', 1, 'ENABLED', 1, 510, 0)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
