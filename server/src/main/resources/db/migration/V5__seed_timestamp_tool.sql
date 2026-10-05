-- Metadata only. Timestamp conversion is implemented by the Android timestamp ToolDefinition.
-- Keep an existing operator-managed record, including disabled or deleted state.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'timestamp', '时间戳转换', 'Unix 时间戳与日期时间互相转换、当前时间与多时区，本地计算。',
    (SELECT id FROM tool_category WHERE code = 'DEV'),
    'timestamp', JSON_ARRAY('时间戳', 'timestamp', 'unix', 'epoch', '时间转换', '日期转换', '时区'),
    'LOCAL', 0, 'ENABLED', 1, 110, 1
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
