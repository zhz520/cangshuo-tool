-- Pinyin conversion executes locally on the Android client over the bundled MIT pinyin-data
-- table plus the curated phrase dictionary; the server only registers catalog metadata.
-- Preserve operator edits, state and deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'pinyin', '汉字转拼音', '支持声调符号、声调数字、无声调、首字母与多音字查询，离线字库覆盖四万余个汉字，并识别常用多音字词组。',
    (SELECT id FROM tool_category WHERE code = 'TEXT'),
    'pinyin', JSON_ARRAY('拼音', '汉字转拼音', 'pinyin', '声调', '首字母', '多音字', 'pinyin converter'),
    'LOCAL', 0, 'ENABLED', 1, 95, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
