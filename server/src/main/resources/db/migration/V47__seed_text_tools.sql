-- Metadata only: these tools execute in the Android client.
-- Preserve existing operator-managed entries, including disabled/deleted records.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES
(
    'password_gen', '随机密码生成', '用安全随机数生成高强度密码，可配置字符集、排除易混淆字符并评估熵值。',
    (SELECT id FROM tool_category WHERE code = 'TEXT'),
    'password_gen', JSON_ARRAY('password', '密码', '随机密码', '密码生成', '强密码', 'password generator', 'strong password'),
    'LOCAL', 0, 'ENABLED', 1, 60, 0
),
(
    'text_counter', '字数统计', '统计字符、中文、英文单词、行数、段落与 UTF-8 体积，并估算阅读时间。',
    (SELECT id FROM tool_category WHERE code = 'TEXT'),
    'text_counter', JSON_ARRAY('count', '字数', '字数统计', '字符统计', '单词数', 'word count', 'character count'),
    'LOCAL', 0, 'ENABLED', 1, 65, 0
),
(
    'url_extract', '网址提取', '从文本中提取链接，支持仅完整链接或包含 www 与常见裸域名，去重后可复制为纯文本或 Markdown。',
    (SELECT id FROM tool_category WHERE code = 'TEXT'),
    'url_extract', JSON_ARRAY('url', '链接', '网址提取', '提取链接', 'url extract', 'link extractor'),
    'LOCAL', 0, 'ENABLED', 1, 70, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
