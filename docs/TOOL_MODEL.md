# 工具模型、注册中心与客户端执行契约

Phase 1 已建立 `ToolDefinition` 和 `ToolRegistry` 基础实现。Android 使用 `core/model` 与 `core/tool` 包；注册中心已通过 Repository/UseCase 接入首页。当前没有具体工具实现，目录请求按对应任务接入；页面数据与状态见 [Android 首页说明](ANDROID_HOME.md)。

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

简短说明统一使用现有 API 契约的 `description` 字段，规格中的 `shortDescription` 统一为该命名。此模型是客户端领域数据；`core/network/model` 已增加 `ToolCatalogDto` 和 `ToolCatalogPageDto` 传输结构，保持接口字段名，未知分类、模式、状态或无效字段映射为 null。JSON 解析器、网络调用及缓存仍待接入；远程数据在 Repository 边界校验和映射后交给领域模型，详见 [目录接口说明](TOOL_CATALOG.md)。

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

可用性检查只表示设备支持能力。注册中心先检查目录状态，再调用 `isAvailable`；打开工具的公共流程还需要检查登录要求和权限。登录和权限处理随导航及相关业务任务接入。

服务端目录仅提供数据和配置；所需 Android 权限、设备检查及可执行页面由客户端实现控制。远程目录不改变 Manifest，不触发权限申请，也不加载动态代码。没有已注册实现的目录条目不能作为可执行工具。

## ToolRegistry

`ToolRegistry` 使用构造参数接收客户端已经实现的 `ToolDefinition` 集合，由 Repository/UseCase 接入首页。它不依赖全局可变单例，方便按应用当前配置建立注册中心。

构造时按 `metadata.code` 建立索引，拒绝重复编码，包括已关闭和维护中的定义；同一个定义重复传入也会报错。`ToolDefinition.code` 必须与元数据编码一致。错误为固定消息的 `IllegalArgumentException`，构造失败时不返回部分注册结果。

注册中心复制定义集合，公开列表为不可修改的列表；调用方修改传入集合不会改变已有注册项。定义对象自身仍被引用，其编码和元数据在注册中心实例存续期间应保持稳定；配置变化由上层建立新实例。所有列表按 `sortOrder` 升序、同序时按 `code` 升序排列，与传入集合顺序无关。

| 入口 | 用途与返回结果 |
| --- | --- |
| `tools` | 全部已注册定义，包含关闭和维护中的工具 |
| `find(code)` | 精确匹配编码，未找到返回 `null`；此方法仅查找实现 |
| `enabledTools(category)` | 返回启用工具，可按分类筛选；默认查询所有分类 |
| `featuredTools(category)` | 返回启用且 `isFeatured=true` 的工具，可按分类筛选 |
| `resolve(code, context)` | 返回 `ToolLookupResult`，先查注册项和目录状态，再检查设备支持 |

`enabledTools` 和 `featuredTools` 保留设备不支持的启用条目，便于界面显示不可用原因；打开前调用 `resolve`。注册中心不会把编码去空白或转换大小写。

| `ToolLookupResult` | 含义 |
| --- | --- |
| `Available(tool)` | 工具已注册、启用且设备支持；携带本地实现 |
| `NotFound` | 未注册该编码，包括空白或未知编码 |
| `Disabled` | 已注册但已关闭，不调用设备支持检查 |
| `Maintenance` | 已注册但维护中，不调用设备支持检查 |
| `Unsupported` | 已启用，但设备不支持 |

`Available` 表示通过注册中心的状态和设备检查，打开流程仍需校验登录、权限和云端工具所需的网络条件。注册中心不申请权限、不保存 Context、不自动打开页面、不读写文件、不发起目录或工具执行请求。

空集合是合法输入：列表为空、`find` 返回 `null`、`resolve` 返回 `NotFound`。首页据此显示空状态，具体工具完成后加入 `ToolboxAppContainer` 中的定义列表。当前该列表为空，查询结果已接入首页。

## 本次验证

### ToolDefinition

2026-10-03 使用本机 Android Studio 自带 JDK `21.0.9`，执行 `:app:assembleDebug :app:lintDebug`：

- Gradle 构建成功，用时 39 秒，包含新增五个 Kotlin 文件的编译，生成 Debug APK。
- Lint 成功，0 错误、6 警告，均为已有 SDK/依赖版本提示及应用图标待完善项。
- 没有新增或执行自动化测试，未验证运行时边界输入或设备行为。

### ToolRegistry

2026-10-03 使用本机 Android Studio 自带 JDK `21.0.9`，执行 `:app:assembleDebug :app:lintDebug`：

- Gradle 构建成功，用时 34 秒，新增注册中心和结果类型编译通过，生成 Debug APK。
- Lint 成功，0 错误、6 个既有警告，仍为 SDK/依赖版本提示及应用图标待完善项。
- 没有新增或执行自动化测试；重复编码、排序、分类筛选与设备支持分支尚未经过运行时验证。

该次验证时页面仍为启动壳；后续首页任务已接入注册中心，验证记录见 [Android 首页说明](ANDROID_HOME.md)。
