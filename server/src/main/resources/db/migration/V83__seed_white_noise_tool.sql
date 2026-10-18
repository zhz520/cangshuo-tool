-- Metadata only. Sound synthesis and playback state stay on the Android device.
-- Preserve operator edits, configuration, state, timestamps and soft deletion on replay.
INSERT INTO tool_definition (
    tool_code, name, description, category_id, icon, keywords_json,
    mode, requires_login, status, version, sort_order, is_featured
) VALUES (
    'white_noise', '白噪音',
    '在本机合成白、粉、褐噪音和雨声、海浪、风扇等环境声，可混音、调节音量并设置睡眠定时。',
    (SELECT id FROM tool_category WHERE code = 'LIFE'),
    'white_noise',
    JSON_ARRAY('白噪音', '粉噪音', '褐噪音', '雨声', '海浪', '助眠', 'white noise', 'soundscape', 'sleep'),
    'LOCAL', 0, 'ENABLED', 1, 635, 0
)
ON DUPLICATE KEY UPDATE tool_code = tool_code;
