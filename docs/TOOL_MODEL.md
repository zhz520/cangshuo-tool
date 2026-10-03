# 工具模型与客户端执行契约

本次完成 Phase 1 的 `ToolDefinition` 基础任务。Android 新增 `core/model` 与 `core/tool` 包，继续使用现有 app 模块及依赖。当前没有注册工具、工具页面或目录请求；注册中心、首页和目录 API 在各自任务中接入。

## ToolMetadata

`ToolMetadata` 保存目录数据，`ToolDefinition` 通过 `metadata` 提供统一的元数据入口。接口上的名称、编码、分类等属性直接读取同一个值。

| Android 字段 | 目录契约字段 | 约定 |
| --- | --- | --- |
| `code` | `code` | 1–64 个字符，小写字母开头，后续为小写字母、数字或下划线；注册中心负责检查重复编码 |
| `name` | `name` | 非空白，最多 128 个字符 |
| `description` | `description` | 简短说明，最多 500 个字符，可为空字符串 |
| `category` / `categoryCode` | `categoryCode` | 类型为 `ToolCategory`，编码通过 `category.code` 获取 |
| `icon` | `icon` | 可空的内置图标名称，最多 128 个字符，小写字母开头，后续为小写字母、数字或下划线 |
| `keywords` | `keywords` | 可为空列表，每项不能是空白文本 |
| `mode` | `mode` | `LOCAL`、`SERVER`、`HYBRID` |
| `requiresLogin` | `requiresLogin` | 默认 `false` |
| `status` | `status` | `ENABLED`、`DISABLED`、`MAINTENANCE`，默认 `ENABLED` |
| `version` | `version` | 正整数，默认 `1` |
| `sortOrder` | `sortOrder` | 整数，默认 `0`，允许负数，后续按升序展示 |
| `isFeatured` | `isFeatured` | 默认 `false` |

模型在构造及 `copy` 时校验编码、文本、图标名、关键词和版本。校验异常使用固定提示，不包含传入内容。图标名称需要在 UI 中映射为已打包资源；名称格式合法也不意味着设备上存在该图标。

简短说明统一使用现有 API 契约的 `description` 字段，规格中的 `shortDescription` 统一为该命名。此模型是客户端领域数据，尚未增加 JSON DTO、解析器或网络调用。远程响应需要先在 Repository 边界校验和映射，再交给领域模型。

## 分类

13 个稳定编码与规格及已有数据库种子一致：

`CALC`、`CONVERT`、`TEXT`、`DEV`、`QR`、`IMAGE`、`PDF`、`DEVICE`、`SENSOR`、`NETWORK`、`LIFE`、`AI`、`OTHER`。

持久化和传输使用分类编码，不使用枚举序号。`ToolCategory.fromCode` 精确匹配编码，遇到未知值返回 `null`；调用方后续处理未知分类，不自动归类为 `OTHER`。分类显示名称在界面接入时从 Android resources 获取。

## ToolDefinition

具体工具实现提供以下内容：

- `metadata`：统一目录元数据；内置工具的显示名称和说明从 Android resources 读取。
- `requiredPermissions`：客户端代码声明的 Android 权限列表，无需权限时显式使用空列表。
- `isAvailable(context)`：检查设备是否支持工具所需能力，例如传感器。该检查不申请权限、不发起网络请求、不读写用户文件。
- `Screen()`：已编译进客户端的 Compose 页面入口。页面状态及业务逻辑遵循 Compose → ViewModel → UseCase → Repository。

可用性检查只表示设备支持能力。打开工具的公共流程还需要分别检查目录状态、登录要求和权限；不能仅凭 `isAvailable=true` 打开已关闭或维护中的工具。上述打开流程由后续注册中心和导航任务实现。

服务端目录仅提供数据和配置；所需 Android 权限、设备检查及可执行页面由客户端实现控制。远程目录不改变 Manifest，不触发权限申请，也不加载动态代码。没有已注册实现的目录条目不能作为可执行工具。

## 本次验证

2026-10-03 使用本机 Android Studio 自带 JDK `21.0.9`，执行 `:app:assembleDebug :app:lintDebug`：

- Gradle 构建成功，用时 39 秒，包含新增五个 Kotlin 文件的编译，生成 Debug APK。
- Lint 成功，0 错误、6 警告，均为已有 SDK/依赖版本提示及应用图标待完善项。
- 没有新增或执行自动化测试，未验证运行时边界输入或设备行为。

当前页面仍为启动壳，注册中心、设备运行及工具执行将在后续任务验证。
