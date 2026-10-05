-- Metadata only. Unit conversion is implemented by the Android unit_converter ToolDefinition.
-- Keep an existing operator-managed record, including disabled or deleted state.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'unit_converter', '单位转换', '长度、面积、体积、质量、温度等多维度常用单位换算，本地计算。',
    (SELECT id FROM tool_category WHERE code = 'CONVERT'),
    'converter', JSON_ARRAY('单位转换', '单位换算', '换算', '长度', '面积', '体积', '重量', '质量', '温度', '速度', '数据', '时间', '压力', '功率', '能量', '角度', 'unit converter', 'convert'),
    'LOCAL', 0, 'ENABLED', 1, 100, 1
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
