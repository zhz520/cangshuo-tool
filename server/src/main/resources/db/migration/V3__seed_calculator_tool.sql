-- Metadata only. Calculation is implemented by the Android calculator ToolDefinition.
-- Keep an existing operator-managed record, including disabled or deleted state.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'calculator', '计算器', '四则运算、小数、括号和百分比，本地计算。',
    (SELECT id FROM tool_category WHERE code = 'CALC'),
    'calculator', JSON_ARRAY('计算器', '计算', '四则运算', 'calculator', 'arithmetic'),
    'LOCAL', 0, 'ENABLED', 1, 10, 1
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
