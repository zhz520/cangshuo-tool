INSERT INTO tool_definition (
    tool_code,name,description,category_id,icon,keywords_json,mode,requires_login,status,version,sort_order,is_featured
) VALUES (
    'compass','指南针','显示磁北方向、方位角和传感器精度，支持暂停与复制分享。',
    (SELECT id FROM tool_category WHERE code='SENSOR'),'compass',
    JSON_ARRAY('指南针','方向','磁北','方位','compass','heading','north'),
    'LOCAL',0,'ENABLED',1,320,0
) ON DUPLICATE KEY UPDATE tool_code=tool_code;
