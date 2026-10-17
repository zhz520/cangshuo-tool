-- Metadata only. Colour picking from images is implemented by the Android color_picker ToolDefinition.
-- Pick, preview and copy HEX/RGB/HSL values with bounded local decoding.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'color_picker', '取色器', '从图片中取色，点击或拖动取样并复制 HEX、RGB、HSL 颜色值。',
    (SELECT id FROM tool_category WHERE code = 'IMAGE'),
    'color_picker', JSON_ARRAY('取色', '吸管', '取色器', '颜色', '图片', 'color picker', 'eyedropper', 'hex'),
    'LOCAL', 0, 'ENABLED', 1, 105, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
