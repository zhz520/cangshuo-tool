-- Reaction samples stay on the Android device. Only shared tool metadata is seeded.
-- Replays preserve all operator edits, soft deletion and usage records.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'reaction_test', '反应力测试',
    '随机提示与抢跑判定，支持多轮反应计时、成绩统计、历史和分享。',
    (SELECT id FROM tool_category WHERE code = 'OTHER'),
    'reaction_test',
    JSON_ARRAY('反应力', '反应速度', '反应测试', '手速', 'reaction', 'reflex', 'benchmark'),
    'LOCAL', 0, 'ENABLED', 1, 638, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
