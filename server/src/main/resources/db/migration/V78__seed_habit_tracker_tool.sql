-- Metadata only. Habits and check-ins remain local to Android.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'habit_tracker', '习惯打卡',
    '在本机建立习惯并按星期安排打卡，查看连续天数、完成率与最近 28 天记录。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'habit_tracker',
    JSON_ARRAY('习惯', '打卡', '连续', '坚持', '目标', '日程', 'habit', 'streak', 'routine'),
    'LOCAL', 0, 'ENABLED', 1, 630, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
