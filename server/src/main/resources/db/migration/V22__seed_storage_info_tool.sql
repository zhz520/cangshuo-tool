INSERT INTO tool_definition (
    tool_code,name,description,category_id,icon,keywords_json,mode,requires_login,status,version,sort_order,is_featured
) VALUES (
    'storage_info','存储信息','查看可访问存储卷的容量、已用与可用空间，支持刷新、复制与分享。',
    (SELECT id FROM tool_category WHERE code='DEVICE'),'storage_info',
    JSON_ARRAY('存储','空间','容量','SD卡','storage','disk','capacity'),
    'LOCAL',0,'ENABLED',1,220,0
) ON DUPLICATE KEY UPDATE tool_code=tool_code;
