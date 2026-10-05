# Android 首页

本次实现 Phase 1 的首页基础任务，替换旧 Compose 启动壳，并将 `ToolRegistry` 接入应用。首页、工具、收藏、我的四个底部入口可以切换；后续已接入本地计算器、[本地搜索](LOCAL_SEARCH.md)、[本地收藏](LOCAL_FAVORITES.md)和 [本地最近使用](LOCAL_RECENT.md)，更多工具、账号及远程目录按各自路线图任务继续开发。

2026-10-05「我的」页已接入邮箱注册、登录、账号展示与退出；使用同一 application-scoped AuthRepository，OpenHomeToolUseCase 在每次打开时读取登录状态。`requiresLogin` 在已登录时通过，退出/过期后恢复门槛；本机收藏/最近使用保持现有数据。当前会话驻留内存，持久登录/刷新会话为下一项；见 [认证说明](AUTH.md)。

## 数据与状态

```text
ToolboxApplication / ToolboxAppContainer / MainActivity
  → HomeRoute / HomeScreen
  → HomeViewModel
  → GetHomeUseCase / OpenHomeToolUseCase
  → HomeRepository / RegistryHomeRepository
  → ToolRegistry
```

`ToolboxApplication` 持有当前手动依赖注入入口 `ToolboxAppContainer`，创建固定定义集合、应用级 Room 实例、Repository、UseCase 与 ViewModel factory。完成具体工具后，将其定义加入这里的集合；当前已注册首个 [本地计算器](CALCULATOR.md)，没有注册占位工具。

Repository 同步读取共享 `ToolRegistryStore` 的当前启用目录并复制关键词列表；此接口只访问内存，不执行阻塞 I/O。首页首屏直接使用内置目录，不等待网络；应用启动时独立异步请求 WEB 目录，成功后发布快照。首页、搜索、收藏及最近使用自动观察变化，具体行为和测试范围见 [目录更新说明](TOOL_CATALOG_SYNC.md)。

ViewModel 公开 `StateFlow<HomeUiState>`，UI 使用 `collectAsStateWithLifecycle` 按生命周期收集。目录有 Loading、Content、Empty、Error 状态，错误页面支持重试。错误消息来自资源，不显示原始异常，也不记录工具输入或异常原文。

`SavedStateHandle` 保存当前底部入口、分类编码和搜索页是否打开；搜索 ViewModel 独立保存查询与分类。配置变化由 ViewModel 保留状态；进程重建时恢复系统保存的页面条件。当前打开的工具实例不写入 SavedStateHandle，进程重建后回到搜索页或所在目录。上述恢复行为尚未通过设备运行验证。

首页、搜索与工具详情之间，以及底部四个入口之间的切换动画统一由 `core/ui/ToolboxMotion.kt` 提供；规则与验收要求见 [Android UI 规范](ANDROID_UI_SPEC.md#页面切换动画统一规范)。动画不改变上述 SavedState 恢复行为。

## 页面行为

| 区域 | 当前行为 |
| --- | --- |
| 首页 | 搜索入口、分类、常用工具、推荐和最近使用列表 |
| 工具 | 显示注册中心的启用工具，支持全部/13 个分类筛选与清除筛选，并提供搜索入口 |
| 常用工具 | 取目录排序后的前 6 个，当前不依据用户历史排序 |
| 推荐工具 | 仅显示目录中 `isFeatured=true` 的启用条目；没有推荐时隐藏此区 |
| 分类 | 显示真实启用工具数量，点击进入对应的工具列表 |
| 收藏 | 显示本机 Room 书签、数量、加载/空/错误状态，支持取消、重试与打开工具 |
| 我的 | 展示访客模式与账号/云同步开放状态 |
| 搜索 | 进入本地搜索页，支持查询、分类、相关度排序与打开已注册工具，见搜索说明 |
| 最近使用 | 成功打开工具后自动记录；按最近时间展示、可重新打开，并支持确认后清除 |

工具卡片采用 2–5 列网格，随窗口宽度调整；内容可滚动，浅色/深色沿用系统主题。界面文案提供简体中文和英语，默认跟随系统，数量使用 plurals；语言配置变化时重新读取目录显示数据，具体规则见 [多语言说明](ANDROID_LOCALIZATION.md)。图标名只映射到内置资源，当前支持 `tools`、`toolbox`、`calculator`；其余名字使用通用图标，不作为资源路径执行。

空注册集合显示“工具正在准备中”和 0 个工具；空分类显示说明及查看全部分类按钮。浏览全部工具的按钮会清除分类筛选。当前实际集合中有 1 个计算器，因此首页和计算分类从真实定义显示计算器，推荐区也使用该定义。工具卡片和标题栏已接入统一收藏星标，收藏状态与搜索页共享；最近使用已在首页接入本机记录，读取中、空、失败与清除状态复用共享加载效果。收藏与最近使用的持久化及验证范围见各自说明。

## 工具打开入口

工具卡片选择经过 ViewModel → UseCase → Repository → Registry，按注册、目录状态、设备支持依次判断。访客模式下，要求登录的工具显示提示；缺少已声明权限的工具显示权限提示，不在首页申请权限。通过检查后，容器承载已注册定义的 Compose `Screen()`，提供标题与返回入口。

未注册、关闭、维护、设备不支持及打开异常均使用友好提示。网络条件与执行错误由相应工具的 ViewModel/UseCase 处理。计算器无需登录、网络或权限，其执行链路已接入；权限申请和登录按后续任务实现。工具打开路径和错误分支尚未运行验证。

## 在 Android Studio 查看

1. 打开仓库中的 `android/`，执行 **Sync Project with Gradle Files**。
2. 选择模拟器或真机，运行 `app`，即可查看首页、分类和四个底部页面。
3. 也可打开 `HomeScreen.kt` 的 Compose Preview 查看空目录布局；这是空状态预览。运行应用的实际集合已注册计算器，计算器的中英文预览位于 `CalculatorScreen.kt`。

## 验证记录

2026-10-03 至 2026-10-04 使用本机 Android Studio 自带的 JDK 21.0.9、SDK 36 执行 `:app:assembleDebug :app:lintDebug`。最终门禁成功，用时 41 秒，45 个任务中 21 个执行、24 个使用已有产物；生成 Debug APK 与 Lint 报告。Lint 为 0 错误、10 警告，其中 9 条为 SDK、构建工具或依赖版本提示，1 条为尚未设置应用图标。

未新增或执行自动化测试，未启动模拟器或真机；设备布局、交互、恢复及工具打开路径尚待运行验证。APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`，Lint 报告位于 `android/app/build/reports/lint-results-debug.html`。

## 实现依据

- [Android ViewModel factory 与 SavedStateHandle](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories)
- [Compose 状态与按生命周期收集 Flow](https://developer.android.com/develop/ui/compose/state)
- [Lifecycle 2.9.4](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.4)

本次显式声明 Lifecycle `2.9.4`、协程 `1.9.0` 和 Compose Foundation，沿用此前解析结果与现有 BOM。新增 ViewModel Compose 集成使用同一 Lifecycle 版本，AGP、Gradle、Kotlin、Compose BOM 和 SDK 基线保持既有配置。
