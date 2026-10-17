-- Metadata only. Holiday and lunar-date calculation runs in the Android ToolDefinition.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'holiday_countdown', '节日倒数',
    '倒数春节、中秋、国庆等中外节日，显示下一次日期、星期、农历、未来三次与实时倒计时。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'holiday_countdown',
    JSON_ARRAY('节日', '倒数', '倒计时', '农历', '春节', '中秋', '国庆', '圣诞节',
        'holiday', 'countdown', 'festival', 'lunar'),
    'LOCAL', 0, 'ENABLED', 1, 622, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
