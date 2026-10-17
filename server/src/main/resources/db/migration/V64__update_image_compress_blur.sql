-- Update only untouched metadata; operator-managed catalog remains authoritative.
UPDATE tool_definition
SET description = '图片压缩、尺寸调整、镜像、圆角、马赛克、模糊与格式转换，实时预览处理效果与体积对比。',
    keywords_json = JSON_ARRAY('图片', '压缩', '图片压缩', '尺寸调整', '格式转换', '镜像', '翻转', '圆角',
        '马赛克', '模糊', 'blur', 'mosaic', 'rounded', 'flip', 'mirror', 'image', 'compress', 'resize',
        'squoosh', 'luban', 'webp', 'jpeg', 'png')
WHERE tool_code = 'image_compress'
  AND description = '图片压缩、尺寸调整、镜像、圆角、马赛克与格式转换，实时预览处理效果与体积对比。';
