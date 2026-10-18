-- Metadata only. Inventory records stay in the Android Room database.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'inventory_assets', '物品资产',
    '在本机记录物品、位置、数量、价格和到期日期，查看库存价值、低库存与即将到期提醒。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'inventory_assets',
    JSON_ARRAY('物品', '资产', '库存', '价格', '位置', '保质期', 'inventory', 'asset'),
    'LOCAL', 0, 'ENABLED', 1, 633, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
