-- Browser-local PDF processing; Android uses the shared official-site WebView container.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured, config_json
) VALUES (
    'pdf_studio', 'PDF 工作台', '合并 PDF、提取或拆分页面、旋转页面、JPG/PNG 图片转 PDF。',
    (SELECT id FROM tool_category WHERE code = 'PDF'), 'pdf',
    JSON_ARRAY('PDF', '合并', '拆分', '提取', '旋转', '图片转PDF', 'merge', 'split', 'rotate', 'images'),
    'WEB', 0, 'ENABLED', 1, 310, 0, JSON_OBJECT('path', '/tools/pdf_studio/')
) ON DUPLICATE KEY UPDATE tool_code = tool_code;
