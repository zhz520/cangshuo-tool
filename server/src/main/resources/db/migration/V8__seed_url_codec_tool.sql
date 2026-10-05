-- Metadata only. URL encoding/decoding is implemented by the Android url_codec ToolDefinition.
-- Keep an existing operator-managed record, including disabled or deleted state.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'url_codec', 'URL 编解码', 'URL 链接与参数编解码，支持组件级与整条 URL 编码模式，本地计算。',
    (SELECT id FROM tool_category WHERE code = 'DEV'),
    'url_codec', JSON_ARRAY('URL', 'URI', 'urlencode', 'urldecode', '编码', '解码', '转义', '链接', '参数'),
    'LOCAL', 0, 'ENABLED', 1, 140, 1
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
