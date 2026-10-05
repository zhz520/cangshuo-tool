INSERT INTO tool_definition (
    tool_code,name,description,category_id,icon,keywords_json,mode,requires_login,status,version,sort_order,is_featured
) VALUES (
    'device_info','设备信息','离线查看设备、Android、CPU ABI、内存和显示信息，支持刷新、复制与分享。',
    (SELECT id FROM tool_category WHERE code='DEVICE'),'device_info',
    JSON_ARRAY('设备','系统','内存','屏幕','CPU','device','system','memory','display','hardware'),
    'LOCAL',0,'ENABLED',1,210,0
) ON DUPLICATE KEY UPDATE tool_code=tool_code;
