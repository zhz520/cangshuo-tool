-- Metadata only. Work-value calculation runs locally in the Android ToolDefinition.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'work_value', '工作性价比',
    '综合月薪、发薪月数、奖金福利、工作支出、工时与通勤，计算名义时薪和真实时薪。',
    (SELECT id FROM tool_category WHERE code = 'CALC'),
    'work_value',
    JSON_ARRAY('工作性价比', '时薪', '工资', '月薪', '通勤', '加班', '年收入', '工作',
        'salary', 'hourly', 'commute', 'work'),
    'LOCAL', 0, 'ENABLED', 1, 58, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
