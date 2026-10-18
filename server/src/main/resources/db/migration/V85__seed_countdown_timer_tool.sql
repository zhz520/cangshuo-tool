-- Metadata only. Timer state, labels and preferences remain on the Android device.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'countdown_timer', '计时器',
    '设置最长 24 小时的倒计时，支持暂停、增加时间、通知控制和本机状态恢复。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'countdown_timer',
    JSON_ARRAY('计时器', '倒计时', '计时', '提醒', '番茄', 'timer', 'countdown', 'alarm'),
    'LOCAL', 0, 'ENABLED', 1, 637, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
