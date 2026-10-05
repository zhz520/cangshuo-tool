-- Metadata only. Hash calculation is implemented by the Android hash ToolDefinition.
-- Keep an existing operator-managed record, including disabled or deleted state.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'hash', 'Hash 计算器', '常见散列哈希值实时计算，支持 MD5、SHA-1、SHA-224、SHA-256、SHA-384、SHA-512，本地计算。',
    (SELECT id FROM tool_category WHERE code = 'DEV'),
    'hash', JSON_ARRAY('Hash', 'MD5', 'SHA', 'SHA256', 'SHA512', '哈希', '散列', '摘要', '校验', 'checksum'),
    'LOCAL', 0, 'ENABLED', 1, 150, 1
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
