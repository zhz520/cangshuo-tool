-- Metadata only. Base64 encoding/decoding is implemented by the Android base64 ToolDefinition.
-- Keep an existing operator-managed record, including disabled or deleted state.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'base64', 'Base64 编解码', '文本与 Base64 互相编码/解码，支持标准与 URL-Safe 模式、容错解析，本地计算。',
    (SELECT id FROM tool_category WHERE code = 'DEV'),
    'base64', JSON_ARRAY('Base64', 'b64', '编码', '解码', 'encode', 'decode', 'url-safe', 'base64编解码'),
    'LOCAL', 0, 'ENABLED', 1, 130, 1
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
