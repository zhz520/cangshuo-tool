-- One WEB tool; credentials and upstream tokens are never persisted.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured, config_json
) VALUES (
    'zepp_steps', '微信运动步数', '通过 Zepp Life 提交当天步数，支持邮箱与中国区手机号、预设步数和同步指引。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'activity', JSON_ARRAY('微信运动', '刷步', '步数', 'Zepp Life', '小米运动', 'wechat steps', 'zepp'),
    'WEB', 0, 'ENABLED', 1, 510, 0, JSON_OBJECT('webPath', '/tools/zepp_steps/')
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
