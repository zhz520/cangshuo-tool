-- Metadata only: the kinship calculator executes in the Android client.
-- Preserve existing operator-managed entries, including disabled/deleted records.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES
(
    'kinship', '亲戚称呼换算', '输入「爸爸的妈妈」这类关系链，算出标准称谓；支持对方称呼我、关系链与关系合称，数据含 6 段以内约 4800 条关系链。',
    (SELECT id FROM tool_category WHERE code = 'CALC'),
    'kinship', JSON_ARRAY('kinship', '亲戚', '称呼', '亲戚称呼', '关系计算', '辈分', 'kinship calculator', 'family title'),
    'LOCAL', 0, 'ENABLED', 1, 75, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
