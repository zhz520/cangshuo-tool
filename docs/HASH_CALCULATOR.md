# Hash 计算器

Phase 2 开发者核心工具，唯一编码 `hash`，分类 `DEV`，模式 `LOCAL`。无需登录、网络或 Android 权限，默认启用并推荐，排序值为 150。应用入口将 `HashToolDefinition` 注册到现有 `ToolRegistry`，首页、工具列表、开发者分类、搜索及推荐区使用同一份定义。

## 实现分层

```text
HashRoute / HashScreen
  → HashViewModel
  → ComputeHashesUseCase
  → HashRepository / LocalHashRepository
  → java.security.MessageDigest
```

哈希计算完全在设备本地执行，使用标准 Java `java.security.MessageDigest` 原生算法库，不请求任何网络、不记录任何用户输入。界面由响应式 StateFlow 驱动。

## 核心功能

### 1. 多算法同步计算
支持常见主流加密散列与哈希算法：
- **MD5**：128-bit 消息摘要
- **SHA-1**：160-bit 安全哈希
- **SHA-224**：224-bit SHA-2 变体
- **SHA-256**：256-bit 行业通用标准哈希
- **SHA-384**：384-bit 高强度哈希
- **SHA-512**：512-bit 超长哈希摘要

### 2. 格式与大小写选项
- **大写十六进制 (Uppercase Hex)**：支持一键切换十六进制哈希结果为大写（A-F）或小写（a-f），便于不同对接系统的校验要求。

### 3. 操作与交互便捷性
- **实时响应**：输入文本时同步更新全部 6 种算法的哈希值。
- **一键粘贴 / 清空**：快速从系统剪贴板读取填入，或一键清空输入框。
- **单条复制**：每个算法卡片均带有一键复制按钮。
- **全部复制**：一键将所有算法名称及其对应哈希值打包复制到剪贴板。
- **统计信息**：实时展示输入字符数与 UTF-8 字节数。

## 界面与交互规范

- 页面不显示“本地处理/无需登录”等特性宣传语；能力与边界由功能说明和错误状态体现。
- 遵循 Material 3 设计系统，最大内容宽度 600dp，自适应键盘 Insets。
- 哈希摘要文本块采用等宽字体（Monospace）排版，并支持长按自由选中文本。
- 提供完整中英文双语资源支持。

## 服务端目录登记

新增 `V9__seed_hash_tool.sql`，在数据库 `tool_definition` 表中登记元数据：
- 编码：`hash`
- 分类：`DEV`
- 模式：`LOCAL`
- 排序：150
- 状态：`ENABLED`，推荐位 `is_featured = 1`
- 关键词涵盖中英文常用检索词（Hash、MD5、SHA、SHA256、SHA512、哈希、散列、摘要、校验、checksum 等），保持幂等插入。

## 验证与测试建议

1. 在 Android Studio 打开 `android/`，编译运行 `:app:assembleDebug` 与 `:app:lintDebug`。
2. 进入应用，在首页推荐或“开发者”分类中打开“Hash 计算器”。
3. 测试输入文本（如 `Hello World`），校验 MD5（`b10a8db164e0754105b7a99be72e3fe5`）与 SHA-256（`a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e`）。
4. 测试大写十六进制切换。
5. 测试单条复制与“复制全部”功能。
