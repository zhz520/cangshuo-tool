-- Metadata only. Radix conversion is implemented by the Android radix ToolDefinition.
-- Keep an existing operator-managed record, including disabled or deleted state.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'radix', '进制转换', '2-36 任意进制数值互转，支持大数（任意精度）、0x/0b/0o 前缀自动识别与位长统计，本地计算。',
    (SELECT id FROM tool_category WHERE code = 'CALC'),
    'radix', JSON_ARRAY('radix', 'base', 'binary', 'hex', '进制', '进制转换', '二进制', '八进制', '十进制', '十六进制', 'bin', 'oct', 'dec'),
    'LOCAL', 0, 'ENABLED', 1, 25, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
