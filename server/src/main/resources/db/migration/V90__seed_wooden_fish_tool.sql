-- Metadata only. Counts and preferences remain on the Android device.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'wooden_fish', '电子木鱼',
    '轻触木鱼并聆听本机合成音效，支持每日目标、里程碑、自动节奏和计数重置。',
    (SELECT id FROM tool_category WHERE code = 'OTHER'), 'wooden_fish',
    JSON_ARRAY('木鱼', '电子木鱼', '静心', '敲击', '计数', 'wooden fish', 'meditation'),
    'LOCAL', 0, 'ENABLED', 1, 642, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
