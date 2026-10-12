-- Metadata only: the colour converter executes in the Android client.
-- Preserve existing operator-managed entries, including disabled/deleted records.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES
(
    'color_converter', '颜色转换', '在 HEX、RGB、HSL、HSV 之间转换颜色，实时预览并复制结果。',
    (SELECT id FROM tool_category WHERE code = 'CALC'),
    'color_converter', JSON_ARRAY('color', '颜色', '颜色转换', 'HEX', 'RGB', 'HSL', 'HSV', 'color converter', 'hex to rgb'),
    'LOCAL', 0, 'ENABLED', 1, 55, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
