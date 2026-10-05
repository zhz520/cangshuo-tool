-- Decision 019: first WEB tool. The official site hosts /tools/qr_studio/ and the app opens it in
-- the shared WebView container; the native qr tool keeps its LOCAL entry.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'qr_studio', '二维码工作台', '网页版二维码工作台：生成结果裁剪、SVG/JPEG 导出、WPA3 与企业 Wi-Fi、vCard 多值联系人。',
    (SELECT id FROM tool_category WHERE code = 'QR'),
    'qr', JSON_ARRAY('二维码', '条码', 'qrcode', '网页版', 'svg', 'wifi', 'vcard'),
    'WEB', 0, 'ENABLED', 1, 211, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
