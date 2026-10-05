-- Metadata only. Text manipulation and statistics are implemented by the Android text ToolDefinition.
-- Keep an existing operator-managed record, including disabled or deleted state.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'text', '文本处理', '文本多维统计（字符/字数/中英文/行/字节）、大小写转换、排版清理、行排序与去重、查找替换，本地处理。',
    (SELECT id FROM tool_category WHERE code = 'TEXT'),
    'text', JSON_ARRAY('文本', '字数统计', '大小写', '驼峰', '下划线', '排序', '去重', '正则', '替换', '统计', 'text', 'lines'),
    'LOCAL', 0, 'ENABLED', 1, 200, 1
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
