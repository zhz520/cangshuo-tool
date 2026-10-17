-- Metadata only. Calendar and almanac calculation runs locally in the Android ToolDefinition.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'lunar_calendar', '万年历',
    '查看公历月历、农历日期、干支生肖、节气、节日与每日宜忌，支持月份切换和日期详情。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'lunar_calendar',
    JSON_ARRAY('万年历', '日历', '农历', '黄历', '干支', '生肖', '节气', '宜忌',
        'calendar', 'lunar', 'almanac', '老黄历'),
    'LOCAL', 0, 'ENABLED', 1, 623, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
