-- Native coin results remain on the device. Preserve operator edits and deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'coin_flip', '抛硬币', '单次或批量随机投掷，自定义正反面，查看比例、连续次数和历史，分享摘要或 CSV。',
    (SELECT id FROM tool_category WHERE code = 'OTHER'),
    'coin_flip', JSON_ARRAY('抛硬币', '投硬币', '随机', '正反面', 'coin flip', 'coin toss', 'heads tails'),
    'LOCAL', 0, 'ENABLED', 1, 600, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
