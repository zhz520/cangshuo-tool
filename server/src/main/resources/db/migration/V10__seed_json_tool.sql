-- Metadata only. JSON processing is implemented by the Android json ToolDefinition.
-- Keep an existing operator-managed record, including disabled or deleted state.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'json', 'JSON 工具', 'JSON 格式化、紧凑压缩、语法校验、字符串转义与反转义，本地计算。',
    (SELECT id FROM tool_category WHERE code = 'DEV'),
    'json', JSON_ARRAY('JSON', 'json', '格式化', '美化', '压缩', '转义', '校验', 'prettify', 'minify'),
    'LOCAL', 0, 'ENABLED', 1, 160, 1
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
