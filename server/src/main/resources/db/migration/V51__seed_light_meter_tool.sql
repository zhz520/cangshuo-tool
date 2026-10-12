-- Native light sensor readings remain on the device. Keep operator-managed records unchanged.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'light_meter', '光感仪', '测量环境照度，查看趋势与最小、最大、平均值，支持单位切换、参考校准和报告分享。',
    (SELECT id FROM tool_category WHERE code = 'SENSOR'),
    'light_meter', JSON_ARRAY('光感仪', '照度计', '光线', '亮度', 'lux', 'light meter', 'illuminance'),
    'LOCAL', 0, 'ENABLED', 1, 340, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
