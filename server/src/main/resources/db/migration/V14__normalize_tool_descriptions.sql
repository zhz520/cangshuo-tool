-- Align shipped descriptions with the Android Chinese functional descriptions.
-- Match the original seed byte-for-byte to preserve operator-authored descriptions.
-- Update description only; existing catalog configuration and deleted state stay intact.
UPDATE tool_definition
SET description = '四则运算、小数、括号和百分比。'
WHERE tool_code = 'calculator'
  AND CAST(description AS BINARY) = CAST('四则运算、小数、括号和百分比，本地计算。' AS BINARY);

UPDATE tool_definition
SET description = '长度、面积、体积、质量、温度等多维度常用单位换算。'
WHERE tool_code = 'unit_converter'
  AND CAST(description AS BINARY) = CAST('长度、面积、体积、质量、温度等多维度常用单位换算，本地计算。' AS BINARY);

UPDATE tool_definition
SET description = 'Unix 时间戳与日期时间互相转换、当前时间与多时区。'
WHERE tool_code = 'timestamp'
  AND CAST(description AS BINARY) = CAST('Unix 时间戳与日期时间互相转换、当前时间与多时区，本地计算。' AS BINARY);

UPDATE tool_definition
SET description = '快速生成标准 UUID/GUID，支持单条与批量、大写/小写、连字符及括号格式。'
WHERE tool_code = 'uuid'
  AND CAST(description AS BINARY) = CAST('快速生成标准 UUID/GUID，支持单条与批量、大写/小写、连字符及括号格式，本地计算。' AS BINARY);

UPDATE tool_definition
SET description = '文本与 Base64 互相编码/解码，支持标准与 URL-Safe、严格 UTF-8 校验和无损 Hex 查看。'
WHERE tool_code = 'base64'
  AND CAST(description AS BINARY) = CAST('文本与 Base64 互相编码/解码，支持标准与 URL-Safe 模式、容错解析，本地计算。' AS BINARY);

UPDATE tool_definition
SET description = '处理 URI 组件、URI 整体与表单值，支持严格 UTF-8 百分号编解码。'
WHERE tool_code = 'url_codec'
  AND CAST(description AS BINARY) = CAST('URL 链接与参数编解码，支持组件级与整条 URL 编码模式，本地计算。' AS BINARY);

UPDATE tool_definition
SET description = '常见散列哈希值实时计算，支持 MD5、SHA-1、SHA-224、SHA-256、SHA-384、SHA-512。'
WHERE tool_code = 'hash'
  AND CAST(description AS BINARY) = CAST('常见散列哈希值实时计算，支持 MD5、SHA-1、SHA-224、SHA-256、SHA-384、SHA-512，本地计算。' AS BINARY);

UPDATE tool_definition
SET description = 'JSON 格式化、紧凑压缩、语法校验、字符串转义与反转义。'
WHERE tool_code = 'json'
  AND CAST(description AS BINARY) = CAST('JSON 格式化、紧凑压缩、语法校验、字符串转义与反转义，本地计算。' AS BINARY);

UPDATE tool_definition
SET description = '文本多维统计（字符/英文词项/Han 字符/行/字节）、大小写转换、排版清理、行排序与去重、查找替换。'
WHERE tool_code = 'text'
  AND CAST(description AS BINARY) = CAST('文本多维统计（字符/字数/中英文/行/字节）、大小写转换、排版清理、行排序与去重、查找替换，本地处理。' AS BINARY);

UPDATE tool_definition
SET description = '二维码实时生成与识别，支持文本、网址、Wi-Fi 配置，容错率与主题色定制。'
WHERE tool_code = 'qr'
  AND CAST(description AS BINARY) = CAST('二维码本地实时生成与离线识别，支持文本、网址、Wi-Fi 配置，容错率与主题色定制。' AS BINARY);

UPDATE tool_definition
SET description = '图片压缩、尺寸调整与格式转换，实时预览压缩效果与体积对比。'
WHERE tool_code = 'image_compress'
  AND CAST(description AS BINARY) = CAST('本地离线图片压缩、尺寸调整与格式转换，保护隐私，实时预览压缩效果与体积对比。' AS BINARY);
