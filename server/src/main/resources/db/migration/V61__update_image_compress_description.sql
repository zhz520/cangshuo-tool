-- Add mirroring discoverability without overwriting an operator-edited description.
UPDATE tool_definition
SET description = '图片压缩、尺寸调整、镜像与格式转换，实时预览处理效果与体积对比。',
    keywords_json = JSON_ARRAY('图片', '压缩', '图片压缩', '尺寸调整', '格式转换', '镜像', '翻转',
        'flip', 'mirror', 'image', 'compress', 'resize', 'squoosh', 'luban', 'webp', 'jpeg', 'png')
WHERE tool_code = 'image_compress'
  AND description = '本地离线图片压缩、尺寸调整与格式转换，保护隐私，实时预览压缩效果与体积对比。';
