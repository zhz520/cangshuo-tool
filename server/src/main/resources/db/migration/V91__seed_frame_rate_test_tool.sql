-- Only catalog metadata is stored remotely; timing samples and history stay on the device.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'frame_rate_test', '可视帧率测试',
    '观察动态画布的帧回调频率、帧间隔和长帧，支持多档测试、趋势、历史与 CSV 分享。',
    (SELECT id FROM tool_category WHERE code = 'OTHER'), 'frame_rate_test',
    JSON_ARRAY('帧率', '流畅度', '帧间隔', '长帧', 'fps', 'frame rate', 'refresh rate'),
    'LOCAL', 0, 'ENABLED', 1, 643, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
