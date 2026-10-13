-- Timing stays on the device. Preserve operator edits, maintenance state and deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'stopwatch', '秒表', '毫秒级计时，支持暂停继续、计次对比、保持屏幕常亮与 CSV 分享。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'stopwatch', JSON_ARRAY('秒表', '计时', '计次', '计时器', 'stopwatch', 'timer', 'laps'),
    'LOCAL', 0, 'ENABLED', 1, 620, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
