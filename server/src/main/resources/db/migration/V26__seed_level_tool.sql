INSERT INTO tool_definition (
    tool_code,name,description,category_id,icon,keywords_json,mode,requires_login,status,version,sort_order,is_featured
) VALUES (
    'level','水平仪','显示双轴倾角和气泡位置，支持暂停、相对归零和复制分享。',
    (SELECT id FROM tool_category WHERE code='SENSOR'),'level',
    JSON_ARRAY('水平仪','倾角','气泡','水平','level','tilt','bubble'),
    'LOCAL',0,'ENABLED',1,330,0
) ON DUPLICATE KEY UPDATE tool_code=tool_code;
