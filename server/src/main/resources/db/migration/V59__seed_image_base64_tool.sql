-- Metadata only. Image Base64 conversion and preview run in the Android client.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'image_base64', '图片 Base64', '图片与 Base64/Data URL 双向转换，预览、复制并保存分享文本。',
    (SELECT id FROM tool_category WHERE code = 'IMAGE'),
    'image_base64', JSON_ARRAY('图片', 'Base64', 'Data URL', '编码', '解码', 'image', 'data url', 'base64 image'),
    'LOCAL', 0, 'ENABLED', 1, 106, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
