-- Metadata only. World-clock calculation runs locally in the Android ToolDefinition.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'world_clock', '世界时间',
    '查看多时区实时时间、日期日差、UTC 偏移与夏令时，支持城市搜索、12/24 小时制和分享。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'world_clock',
    JSON_ARRAY('世界时间', '时区', '城市时间', '世界时钟', 'UTC', '换算', '夏令时',
        'world clock', 'timezone', 'clock'),
    'LOCAL', 0, 'ENABLED', 1, 621, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
