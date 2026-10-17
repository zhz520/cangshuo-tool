-- Metadata only. Banner text and rendering stay in Android SavedStateHandle and Compose.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'handheld_banner', '手持弹幕',
    '把文字变成可调速、可镜像的全屏滚动弹幕，支持字号、方向、配色、暂停和屏幕常亮。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'handheld_banner',
    JSON_ARRAY('手持弹幕', '弹幕', '滚动字幕', '走马灯', 'LED', '接机', 'banner', 'marquee', 'ticker', 'scroll'),
    'LOCAL', 0, 'ENABLED', 1, 625, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
