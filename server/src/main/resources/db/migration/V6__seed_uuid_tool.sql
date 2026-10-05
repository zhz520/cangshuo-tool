-- Metadata only. UUID generation is implemented by the Android uuid ToolDefinition.
-- Keep an existing operator-managed record, including disabled or deleted state.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'uuid', 'UUID 生成器', '快速生成标准 UUID/GUID，支持单条与批量、大写/小写、连字符及括号格式，本地计算。',
    (SELECT id FROM tool_category WHERE code = 'DEV'),
    'uuid', JSON_ARRAY('UUID', 'GUID', '生成器', '唯一标识', '随机', 'uuid', 'generator', 'random'),
    'LOCAL', 0, 'ENABLED', 1, 120, 1
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
