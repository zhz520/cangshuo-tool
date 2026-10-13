-- RE2/J execution stays on the device. Preserve operator edits, state and deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'regex_tester', '正则表达式', '测试匹配、捕获分组与替换，支持选项、常用示例和结果复制。',
    (SELECT id FROM tool_category WHERE code = 'DEV'),
    'regex_tester', JSON_ARRAY('正则', '正则表达式', '匹配', '分组', '替换', 'regex',
        'regular expression', 'pattern', 'groups', 'replace'),
    'LOCAL', 0, 'ENABLED', 1, 640, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
