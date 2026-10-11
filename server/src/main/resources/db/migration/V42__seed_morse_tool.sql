-- Metadata only. Morse coding is implemented by the Android morse ToolDefinition.
-- Keep an existing operator-managed record, including disabled or deleted state.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'morse', '摩斯电码', '文本与摩斯电码互转（ITU-R M.1677），支持字母、数字与常用标点，带标准时序音频播放，本地运行。',
    (SELECT id FROM tool_category WHERE code = 'TEXT'),
    'morse', JSON_ARRAY('morse', '电码', '摩斯', '摩尔斯', '滴答', 'sos', 'telegraph', '编码'),
    'LOCAL', 0, 'ENABLED', 1, 155, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
