-- Metadata only. Subscription records and reminder settings stay in the Android Room database.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'subscription_manager', '订阅管理',
    '在本机记录会员与订阅服务、周期金额和下次续费日期，查看月均支出并接收续费提醒。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'subscription_manager',
    JSON_ARRAY('订阅', '会员', '续费', '账单', '到期', '自动续费', 'subscription', 'membership'),
    'LOCAL', 0, 'ENABLED', 1, 634, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
