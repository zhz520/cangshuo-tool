-- Public-link parser. Share passwords are request-scoped and never persisted.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured, config_json
) VALUES (
    'lanzou_direct', '蓝奏直链解析',
    '解析蓝奏云公开分享链接，显示文件名、大小、临时直链和官方回退页；支持分享密码与失败说明。',
    (SELECT id FROM tool_category WHERE code = 'NETWORK'), 'link',
    JSON_ARRAY('蓝奏云', '蓝奏', '直链', '下载', '分享密码', 'lanzou', 'direct link'),
    'WEB', 0, 'ENABLED', 1, 631, 0,
    JSON_OBJECT('webPath', '/tools/lanzou_direct/')
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
