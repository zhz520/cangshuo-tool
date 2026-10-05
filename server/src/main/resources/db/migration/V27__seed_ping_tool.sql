INSERT INTO tool_definition (
    tool_code,name,description,category_id,icon,keywords_json,mode,requires_login,status,version,sort_order,is_featured
) VALUES (
    'ping','Ping','发送少量 ICMP 诊断包，查看往返延迟与丢包，支持取消和复制分享。',
    (SELECT id FROM tool_category WHERE code='NETWORK'),'ping',
    JSON_ARRAY('Ping','延迟','丢包','网络','ICMP','latency','packet loss'),
    'LOCAL',0,'ENABLED',1,410,0
) ON DUPLICATE KEY UPDATE tool_code=tool_code;
