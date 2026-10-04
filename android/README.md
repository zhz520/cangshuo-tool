# Android 客户端

当前包含 Kotlin + Jetpack Compose 首页、工具模型基础契约及注册中心。首页遵循 Compose + ViewModel + UseCase + Repository 分层，提供分类筛选、工具/推荐列表、页面状态与底部入口；UI 文案放在 Android resources 中。布局和数据范围见 [首页说明](../docs/ANDROID_HOME.md)。

## 工具模型

`core/model` 提供 `ToolMetadata`、13 个稳定分类及运行模式、目录状态；`core/tool/ToolDefinition` 统一元数据、Android 权限声明、设备能力检查和 Compose 页面入口。模型与目录字段、数据库基础约定对齐，详细边界见 [工具模型说明](../docs/TOOL_MODEL.md)。

`ToolRegistry` 接收客户端定义集合，检查编码唯一性并提供查找、分类、推荐及状态/设备支持结果。应用入口通过 `ToolboxAppContainer` 将其接入首页，具体工具完成后加入定义集合。当前已注册 [本地计算器](../docs/CALCULATOR.md)，支持四则运算、小数、括号、百分号、复制及继续计算；目录远程刷新、搜索、收藏与最近使用按后续任务实现。

界面支持简体中文和英语，默认跟随系统语言，未匹配支持语言时回退中文。Android 13+ 的系统应用语言设置可单独选择中文、英语或系统默认；更早版本跟随系统。范围及扩展规则见 [多语言说明](../docs/ANDROID_LOCALIZATION.md)。

服务端已提供 `GET /api/v1/tools` 列表实现，客户端 `core/network/model` 已同步 `ToolCatalogDto` 与 `ToolCatalogPageDto`，提供到领域元数据的安全映射。JSON 解析、网络请求、缓存和首页合并仍待远程刷新任务接入，当前首页不请求接口。目录与验证范围见 [目录接口说明](../docs/TOOL_CATALOG.md)。

## SDK 基线

- `minSdk`: 26
- `compileSdk`: 36
- `targetSdk`: 36
- Compose UI 依赖由 Compose BOM 2026.06.01 管理
- Android Gradle Plugin: 9.1.0；Gradle Wrapper: 9.3.1
- 使用 AGP 内置 Kotlin 2.2.10，Compose compiler plugin 使用相同版本
- JDK: 建议使用 Android Studio 自带的 JDK 21（AGP 最低要求为 JDK 17）

构建需要 Android SDK Platform 36 和 Build Tools 36.0.0。版本组合按本机 Android Studio 最高支持 AGP 9.1.0 的限制固定，避免使用需要更高 AGP 和 SDK 的 Compose 版本。

首次构建时，Gradle Wrapper 会下载 Gradle 9.3.1（需要网络）。Windows 下在本目录运行 `.\gradlew.bat :app:assembleDebug`；其他平台运行 `./gradlew :app:assembleDebug`。`local.properties` 由本机 Android Studio 管理，已加入 Git 忽略规则。

Wrapper 分发包已配置官方 SHA-256 校验。GitHub Actions 构建 Debug APK、执行 Lint 并保存报告；工作流及验证范围见 [CI 说明](../docs/CI.md)。

在 Android Studio 中打开本目录，执行 **Sync Project with Gradle Files**。Gradle JDK 选择 Android Studio 自带的 JDK；同步完成后选择模拟器或已连接的 Android 设备，运行 `app`。

兼容性参考：[AGP 9.1 发行说明](https://developer.android.com/build/releases/agp-9-1-0-release-notes)。

## 构建验证

2026-10-04 使用本机 Android Studio 自带的 JDK 21.0.9 完成计算器和语言配置构建门禁：

- `:app:assembleDebug`：成功，生成 `app/build/outputs/apk/debug/app-debug.apk`。
- `:app:lintDebug`：成功，0 错误、11 警告。其中 9 条为 SDK、构建工具或依赖的新版本提示，1 条为尚未设置应用图标，1 条为 `localeConfig` 仅在 API 33+ 生效的提示。

当前版本按 IDE 兼容范围固定，应用图标在后续视觉完善时补充。Lint 报告位于 `app/build/reports/lint-results-debug.html`。未新增或执行自动化测试，计算行为和设备运行尚待验证；本次范围与记录见 [计算器说明](../docs/CALCULATOR.md)。
