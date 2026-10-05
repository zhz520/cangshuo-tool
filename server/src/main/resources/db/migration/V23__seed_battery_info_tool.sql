INSERT INTO tool_definition (
    tool_code,name,description,category_id,icon,keywords_json,mode,requires_login,status,version,sort_order,is_featured
) VALUES (
    'battery_info','电池信息','查看电量、充电状态、温度、电压和系统报告的电池数据。',
    (SELECT id FROM tool_category WHERE code='DEVICE'),'battery_info',
    JSON_ARRAY('电池','电量','充电','温度','battery','charging','power'),
    'LOCAL',0,'ENABLED',1,230,0
) ON DUPLICATE KEY UPDATE tool_code=tool_code;
