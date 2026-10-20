-- Metadata only. Vibration testing history stays on the device.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'vibrator_patterns', '振动器',
    '测试振动强度与节奏，提供预置波形、自定义时长、安全停止和历史记录。',
    (SELECT id FROM tool_category WHERE code = 'OTHER'), 'vibrator_patterns',
    JSON_ARRAY('振动', '振动器', '振动测试', '马达', '触感', 'haptic', 'vibration'),
    'LOCAL', 0, 'ENABLED', 1, 641, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
