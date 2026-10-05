-- Correct the qr_studio description: the shipped page controls size and quiet zone, not crop.
-- Match the V16 seed byte-for-byte and update description only; other configuration stays intact.
UPDATE tool_definition
SET description = '网页版二维码工作台：尺寸与留白控制、SVG/JPEG 导出、WPA3 与企业 Wi-Fi、vCard 多值联系人。'
WHERE tool_code = 'qr_studio'
  AND CAST(description AS BINARY) = CAST('网页版二维码工作台：生成结果裁剪、SVG/JPEG 导出、WPA3 与企业 Wi-Fi、vCard 多值联系人。' AS BINARY);
