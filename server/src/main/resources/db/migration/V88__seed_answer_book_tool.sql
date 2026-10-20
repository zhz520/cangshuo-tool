-- Metadata only; favorites, draw cycles and reading history stay on the device.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'answer_book', '答案之书',
    '按主题随机翻阅灵感短句，支持一轮不重复、收藏、历史和分享。',
    (SELECT id FROM tool_category WHERE code = 'OTHER'),
    'answer_book', JSON_ARRAY('答案之书', '灵感', '随机答案', '翻书', 'answer', 'book', 'inspiration'),
    'LOCAL', 0, 'ENABLED', 1, 640, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
