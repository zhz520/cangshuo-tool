-- Metadata only. Match scores, timer and history stay in the Android local SavedStateHandle.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'scoreboard', '记分牌',
    '为两队记录比分、定时比赛、切换节次，支持撤销、换边、历史记录与分享。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'scoreboard',
    JSON_ARRAY('记分牌', '计分', '比分', '两队', '计时', '比赛', '裁判', 'scoreboard', 'score', 'timer', 'match'),
    'LOCAL', 0, 'ENABLED', 1, 624, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
