-- Metadata only. Clock rendering and preferences remain local to the Android app.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'time_screen', '时间屏幕',
    '将设备变成全屏数字时钟，支持 12/24 小时、秒数与日期、主题强调色、防烧屏漂移和屏幕常亮。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'time_screen',
    JSON_ARRAY('时间屏幕', '全屏时钟', '桌面时钟', '数字时钟', '防烧屏', '时间显示',
        'clock', 'screensaver', 'fullscreen clock', 'time display'),
    'LOCAL', 0, 'ENABLED', 1, 626, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
