-- Catalog metadata only; tap samples and results remain on the Android device.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'tap_speed', '手速测试',
    '多档时长与单指或多指点击测试，查看每秒点击曲线、CPS、历史并分享成绩。',
    (SELECT id FROM tool_category WHERE code = 'OTHER'),
    'tap_speed', JSON_ARRAY('手速', '点击速度', '点击测试', '连点', 'CPS', 'tap', 'click speed'),
    'LOCAL', 0, 'ENABLED', 1, 639, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
