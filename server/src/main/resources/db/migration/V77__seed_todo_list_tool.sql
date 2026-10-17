-- Metadata only. Tasks and completion state remain local to Android.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'todo_list', '待办事项',
    '在本机管理待办任务，支持完成状态、优先级、截止日期、搜索、筛选、排序和清除已完成事项。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'todo_list',
    JSON_ARRAY('待办', '待办事项', '任务', '清单', '计划', '截止日期', '优先级',
        'todo', 'task', 'checklist', 'planner'),
    'LOCAL', 0, 'ENABLED', 1, 629, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
