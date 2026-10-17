-- Metadata only. Options, weights, results and history remain local to Android.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'decision_wheel', '做个决定',
    '在可自定义的权重转盘中随机选择结果，支持快捷主题、历史记录、复制与分享。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'decision_wheel',
    JSON_ARRAY('做个决定', '随机选择', '转盘', '抽签', '抉择', '选择困难', '权重',
        'decision', 'wheel', 'spinner', 'random', 'picker'),
    'LOCAL', 0, 'ENABLED', 1, 627, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
