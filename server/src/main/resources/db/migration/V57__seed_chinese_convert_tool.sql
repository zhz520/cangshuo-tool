-- OpenCC 1.1.9 phrase/region conversion runs locally with the bundled Android data.
-- The server only registers metadata and reuses catalog, administration and usage APIs.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'chinese_convert', '简繁互转', '按词组转换简繁体，支持台湾与香港字形和地区用词。',
    (SELECT id FROM tool_category WHERE code = 'TEXT'),
    'chinese_convert', JSON_ARRAY('简繁', '简体', '繁体', '台湾', '香港', 'OpenCC', '汉字'),
    'LOCAL', 0, 'ENABLED', 1, 100, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
