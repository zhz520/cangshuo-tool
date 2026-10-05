-- Metadata only. QR code generation and decoding are implemented by the Android qr ToolDefinition.
-- Keep an existing operator-managed record, including disabled or deleted state.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'qr', '二维码工具', '二维码本地实时生成与离线识别，支持文本、网址、Wi-Fi 配置，容错率与主题色定制。',
    (SELECT id FROM tool_category WHERE code = 'QR'),
    'qr', JSON_ARRAY('二维码', 'QR', 'qrcode', '扫码', '生成器', '解析', '识别', 'wifi', '条码'),
    'LOCAL', 0, 'ENABLED', 1, 210, 1
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
