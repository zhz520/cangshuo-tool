# 文件安全（File security）

2026-10-05，Phase 6 File security。上传路径在进入对象存储之前做有界内容校验：声明 MIME 必须与真实魔术字节一致，二进制伪装成文本同样被拒；键由服务端生成，不接受客户端文件名或路径。本项无数据库结构变更。

## 校验规则

| 真实内容 | 检测结果 | 说明 |
| --- | --- | --- |
| `89 50 4E 47 0D 0A 1A 0A` | `image/png` | PNG 签名 |
| `FF D8 FF` | `image/jpeg` | JPEG SOI + 段标记 |
| `RIFF` + 4 字节 + `WEBP` | `image/webp` | 偏移 0 与 8 的双签名 |
| `%PDF-` | `application/pdf` | PDF 版本头 |
| 前 32 字节无 NUL、无非法控制字符 | `text/plain` | 文本启发式，允许 UTF-8 高位字节与 `\t\n\r\x0c` |
| 其他（含 NUL、二进制控制字符、未知签名） | 无 | 直接拒绝 |

- 声明类型必须与检测结果完全相等：`application/pdf` + PNG 内容、`text/plain` + 含 NUL 的二进制都会返回 415/40002；声明类型不在白名单时同样 415/40002。
- 只读取前 32 字节（`BufferedInputStream.mark/reset` 后复用同一流），不做完整解码、不二次读取对象；SHA-256 仍由同一遍上传流计算（见 [对象存储](STORAGE.md)）。
- 白名单拒绝 SVG/HTML/Office 等可执行或主动内容类型，避免在客户端或 WebView 中渲染时引入注入面。
- 键完全由服务端生成（`objects/yyyyMM/<uuid>.<ext>`）；客户端输入只在严格模式与 `..`、`//`、结尾点/斜杠禁止规则下用于 presign/delete。
- 大小在读取前校验：单对象 ≤ `MINIO_MAX_OBJECT_BYTES`（默认 25 MiB），并受 `spring.servlet.multipart` 的 32MB 限制兜底。

## 覆盖范围与边界

当前唯一上传入口是管理员 `/api/v1/admin/storage/objects`；后续面向用户的文件接口必须复用同一 `FileSignatures` + `ObjectStorage` 组合，不得绕过。

不在本项范围：病毒/木马扫描（生产按需接入 ClamAV 等）、图片解码炸弹与像素上限、EXIF 隐私清理、PDF 结构解析、每用户配额与限流（属于 Quotas / Rate limits）、临时对象自动清理（部署任务）。文档明确这些仍是未验证能力。

## 验证

- Server 单元测试 102/102 通过：`FileSignaturesTest` 覆盖四种签名、文本启发式（含制表/换行）、NUL 与未知二进制、`RIFF/AVI` 反例与声明/检测匹配矩阵；`AdminStorageServiceTest` 覆盖声明类型不匹配与二进制伪装成文本的拒绝路径。
- `scripts/check_storage.py` 在真实 MinIO 上执行 27/27 项校验：新增 PNG 声明为 `application/pdf` → 415/40002、含 NUL 二进制声明为 `text/plain` → 415/40002、真实文本上传 201 且 sha256 正确并成功删除；原有上传/预签名/删除/审计用例保持通过。
