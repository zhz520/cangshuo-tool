-- New web tool; replay must preserve operator edits, disabled state and soft deletion.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json, mode,
    requires_login, status, version, sort_order, is_featured, config_json
) VALUES (
    'short_video_parse', '短视频解析', '解析分享链接，提取视频、图集、实况照片和封面，支持预览与选图打包。',
    (SELECT id FROM tool_category WHERE code = 'NETWORK'), 'toolbox',
    JSON_ARRAY('短视频', '无水印', '视频解析', '万能解析', '图集', '封面', '抖音', '快手', '小红书', 'TikTok', 'B站', '微博'),
    'WEB', 0, 'ENABLED', 1, 630, 0,
    JSON_OBJECT('webPath', '/tools/short_video_parse/')
) ON DUPLICATE KEY UPDATE tool_code = tool_code;
