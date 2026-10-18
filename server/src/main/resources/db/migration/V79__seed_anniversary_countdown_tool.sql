-- Metadata only. Anniversary dates remain local to Android.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'anniversary_countdown', '纪念日',
    '在本机记录生日、相识、家庭与重要日子，支持每月或每年重复、置顶、倒计时和农历显示。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'anniversary_countdown',
    JSON_ARRAY('纪念日', '生日', '周年', '倒数', '重要日子', '倒数日', 'anniversary', 'birthday', 'countdown'),
    'LOCAL', 0, 'ENABLED', 1, 631, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
