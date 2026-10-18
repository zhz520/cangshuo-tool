-- Metadata only. Sensitive cycle, symptom, mood and note data stay in the Android local database.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'period_tracker', '经期记录',
    '在本机记录经期起止、流量、症状和情绪，查看日历、历史统计与周期估算。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'period_tracker',
    JSON_ARRAY('经期', '月经', '周期', '排卵', '日历', '健康', 'period', 'cycle'),
    'LOCAL', 0, 'ENABLED', 1, 636, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
