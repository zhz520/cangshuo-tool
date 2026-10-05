INSERT INTO tool_definition (
    tool_code,name,description,category_id,icon,keywords_json,mode,requires_login,status,version,sort_order,is_featured
) VALUES (
    'http_status','HTTP 状态','检查 HTTP(S) 地址的状态码、响应头、跳转与耗时，只读取响应头。',
    (SELECT id FROM tool_category WHERE code='NETWORK'),'http_status',
    JSON_ARRAY('HTTP','状态码','响应头','跳转','诊断','https','status','header','redirect'),
    'LOCAL',0,'ENABLED',1,420,0
) ON DUPLICATE KEY UPDATE tool_code=tool_code;
