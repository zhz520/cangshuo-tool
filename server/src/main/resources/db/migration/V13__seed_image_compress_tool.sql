-- Metadata only. Image compression, resizing and format conversion are implemented by the Android image_compress ToolDefinition.
-- Keep an existing operator-managed record, including disabled or deleted state.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'image_compress', '图片压缩', '本地离线图片压缩、尺寸调整与格式转换，保护隐私，实时预览压缩效果与体积对比。',
    (SELECT id FROM tool_category WHERE code = 'IMAGE'),
    'image_compress', JSON_ARRAY('图片', '压缩', '图片压缩', '尺寸调整', '格式转换', 'image', 'compress', 'resize', 'squoosh', 'luban', 'webp', 'jpeg', 'png'),
    'LOCAL', 0, 'ENABLED', 1, 220, 1
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
