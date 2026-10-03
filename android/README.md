# Android 客户端

当前包含 Kotlin + Jetpack Compose 启动壳、工具模型基础契约及注册中心。后续功能遵循 Compose + ViewModel + UseCase + Repository 分层；UI 文案放在 Android resources 中。

## 工具模型

`core/model` 提供 `ToolMetadata`、13 个稳定分类及运行模式、目录状态；`core/tool/ToolDefinition` 统一元数据、Android 权限声明、设备能力检查和 Compose 页面入口。模型与目录字段、数据库基础约定对齐，详细边界见 [工具模型说明](../docs/TOOL_MODEL.md)。

`ToolRegistry` 接收客户端定义集合，检查编码唯一性并提供查找、分类、推荐及状态/设备支持结果。应用入口和具体工具列表将在首页及工具任务中接入；当前首页仍为启动壳。目录 API、计算器及搜索按 Phase 1 各任务实现。

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

2026-10-03 使用本机 Android Studio 自带的 JDK 21.0.9 验证：

- `:app:assembleDebug`：成功，生成 `app/build/outputs/apk/debug/app-debug.apk`。
- `:app:lintDebug`：成功，0 错误、6 警告。其中 5 条为 SDK 或依赖的新版本提示，1 条为尚未设置应用图标。

当前版本按 IDE 兼容范围固定，应用图标在后续视觉完善时补充。Lint 报告位于 `app/build/reports/lint-results-debug.html`。设备运行尚待在模拟器或真机上确认。
