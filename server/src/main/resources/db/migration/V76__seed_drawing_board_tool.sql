-- Metadata only. Drawings and preferences remain local to Android.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'drawing_board', '画板',
    '在空白画布上自由绘制，支持多色画笔、粗细调节、橡皮擦、撤销重做和 PNG 保存分享。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'drawing_board',
    JSON_ARRAY('画板', '画画', '涂鸦', '手绘', '绘图', '画笔', '橡皮擦',
        'drawing', 'draw', 'sketch', 'canvas', 'paint'),
    'LOCAL', 0, 'ENABLED', 1, 628, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
