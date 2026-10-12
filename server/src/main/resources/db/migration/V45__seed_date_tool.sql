-- Metadata only: the date calculator executes in the Android client.
-- Preserve existing operator-managed entries, including disabled/deleted records.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES
(
    'date_calc', '日期计算器', '计算两个日期的相差天数、周数、年月日与工作日，或按天、周、月、年推算日期。',
    (SELECT id FROM tool_category WHERE code = 'CALC'),
    'date_calc', JSON_ARRAY('date', '日期', '日期计算', '日期间隔', '天数', '工作日', '日期推算', 'date calculator', 'days between'),
    'LOCAL', 0, 'ENABLED', 1, 50, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
