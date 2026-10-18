-- Metadata only. Timer settings and focus history remain local to Android.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'pomodoro_focus', '番茄专注',
    '用专注与休息循环管理时间，支持自定义时长、自动切换、后台计时和本地专注统计。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'pomodoro_focus',
    JSON_ARRAY('番茄', '专注', '计时', '休息', '效率', 'pomodoro', 'focus', 'timer'),
    'LOCAL', 0, 'ENABLED', 1, 632, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
