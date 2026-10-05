INSERT INTO tool_definition (
    tool_code,name,description,category_id,icon,keywords_json,mode,requires_login,status,version,sort_order,is_featured
) VALUES (
    'sensor_info','传感器','查看传感器清单与加速度、陀螺仪、磁场、光线等读数，支持暂停和复制分享。',
    (SELECT id FROM tool_category WHERE code='SENSOR'),'sensor_info',
    JSON_ARRAY('传感器','加速度','陀螺仪','磁场','光线','sensor','accelerometer','gyroscope'),
    'LOCAL',0,'ENABLED',1,310,0
) ON DUPLICATE KEY UPDATE tool_code=tool_code;
