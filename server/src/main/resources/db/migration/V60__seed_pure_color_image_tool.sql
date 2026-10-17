-- Metadata only. Pure-colour PNG generation runs in the Android pure_color_image ToolDefinition.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'pure_color_image', '纯色图制作', '选择颜色与尺寸，生成透明或纯色 PNG，并保存或分享。',
    (SELECT id FROM tool_category WHERE code = 'IMAGE'),
    'pure_color_image', JSON_ARRAY('纯色', '纯色图', '颜色', '背景图', 'PNG', '生成', '图片'),
    'LOCAL', 0, 'ENABLED', 1, 107, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
