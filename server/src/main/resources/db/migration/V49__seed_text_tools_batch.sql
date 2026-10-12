-- Metadata only: these tools execute in the Android client.
-- Preserve existing operator-managed entries, including disabled/deleted records.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES
(
    'number_chinese', '数字转中文', '阿拉伯数字与中文数字互转：小写、大写与人民币金额，支持负数、小数与口语化「十」。',
    (SELECT id FROM tool_category WHERE code = 'TEXT'),
    'number_chinese', JSON_ARRAY('number', '数字转中文', '大写金额', '人民币大写', '中文数字', 'number to chinese', 'rmb uppercase'),
    'LOCAL', 0, 'ENABLED', 1, 80, 0
),
(
    'char_convert', '特殊字符转换', '全角/半角互转、英文大小写转换、压缩空白、移除零宽字符并统一换行符。',
    (SELECT id FROM tool_category WHERE code = 'TEXT'),
    'char_convert', JSON_ARRAY('全角', '半角', '特殊字符', '字符转换', '大写', 'fullwidth', 'halfwidth', 'case convert'),
    'LOCAL', 0, 'ENABLED', 1, 85, 0
),
(
    'text_similarity', '文本相似性', '比较两段文本的编辑距离、相似度、字符集合重合度、最长公共子串与公共前缀。',
    (SELECT id FROM tool_category WHERE code = 'TEXT'),
    'text_similarity', JSON_ARRAY('相似度', '文本比较', '编辑距离', 'Levenshtein', 'similarity', 'diff'),
    'LOCAL', 0, 'ENABLED', 1, 90, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
