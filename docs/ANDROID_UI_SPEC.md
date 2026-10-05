# Android UI 规范

版本：1.2 · 2026-10-04（新增页面切换动画规范；含网页版工具容器约定）。适用于沧烁工具箱 Android 客户端，后续新增页面和工具界面均以此文档为实现基准。

## 1. 设计来源与取舍

用户指定 `stitch_cangshuo_tool_android_ui_redesign/` 作为后续 UI 参考。本次已查看五张截图、对应 HTML，以及 [Clean Slate M3 设计稿](../stitch_cangshuo_tool_android_ui_redesign/clean_slate_m3/DESIGN.md)。

| 参考 | 页面 | 采用的设计规则 |
| --- | --- | --- |
| [_1 截图](../stitch_cangshuo_tool_android_ui_redesign/_1/screen.png) / [HTML](../stitch_cangshuo_tool_android_ui_redesign/_1/code.html) | 工具目录 | 紧凑标题、搜索框、横向分类胶囊、白色双列工具卡片 |
| [_2 截图](../stitch_cangshuo_tool_android_ui_redesign/_2/screen.png) / [HTML](../stitch_cangshuo_tool_android_ui_redesign/_2/code.html) | 首页 | 搜索、快捷工具、分类、最近使用和推荐分区；彩色浅底图标 |
| [_3 截图](../stitch_cangshuo_tool_android_ui_redesign/_3/screen.png) / [HTML](../stitch_cangshuo_tool_android_ui_redesign/_3/code.html) | 计算器 | 返回标题栏、白色结果面板、等宽数值、操作与数字键分组 |
| [_4 截图](../stitch_cangshuo_tool_android_ui_redesign/_4/screen.png) / [HTML](../stitch_cangshuo_tool_android_ui_redesign/_4/code.html) | 收藏 | 收藏数量、列表卡片、星标与启动入口、引导浏览目录 |
| [_5 截图](../stitch_cangshuo_tool_android_ui_redesign/_5/screen.png) / [HTML](../stitch_cangshuo_tool_android_ui_redesign/_5/code.html) | 设置与关于 | 分组设置卡片、行内图标、右侧值/操作、离线状态提示 |

参考稿存在以下差异，本项目统一如下：

- `_2` 使用 `#F8F9FA` / `#1A73E8`，其余四个 HTML 共用 `#FAF8FF` / `#003FB1`。采用后者作为正式浅色主题，保留首页的信息分区方式。
- `DESIGN.md` 的 YAML 和正文有不同色值、圆角与导航高度。颜色以四个页面共用的 HTML token 为准；尺寸以本规范的原生 Android dp/sp 为准。
- 底部四个入口统一命名为「首页、工具、收藏、我的」。收藏使用星标；目录稿的心形收藏图标统一改为星标。
- 保留原生 Material 3 交互、至少 48dp 触控区域和系统安全区；截图中的模拟状态栏、时间、电量与手势横条由 Android 系统呈现。
- 示例中的 36/40 款工具、v2.4、科学函数、拖拽排序、备份、缓存清理、更新检测等不代表已实现功能。数量、版本和状态必须取真实数据；未实现操作不显示成功提示。计算规则沿用本地计算器实现，HTML 中的 `eval` 不进入客户端。

## 2. 视觉原则

- 清晰、克制、工具优先：浅色画布、白色卡片、蓝色操作重点，使用细边框和色阶区分层级。
- 信息顺序稳定：标题 → 输入/筛选 → 操作 → 结果 → 复制/保存/分享。
- 蓝色用于主要操作、选中项和焦点；青绿色用于真实的本地/就绪状态；红色用于错误和删除提示。
- 功能图标可有浅蓝、青绿、紫色等淡色底，但应保持相同尺寸、笔画和圆角。不要每个工具创建独立的主题。
- 使用原生点击反馈；避免发光、重阴影、大渐变、弹跳或持续脉冲效果。

## 3. 主题 token

颜色集中在 `ui/theme/ToolboxColors.kt`，页面通过 `MaterialTheme.colorScheme` 使用；禁止在业务页面复制十六进制颜色。

| Material 3 role | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| primary / onPrimary | `#003FB1` / `#FFFFFF` | `#B5C4FF` / `#002B7F` | 主按钮、选中图标、进度环 |
| primaryContainer / onPrimaryContainer | `#1A56DB` / `#D4DCFF` | `#003FB1` / `#DBE1FF` | 强调容器及其文字 |
| secondary / onSecondary | `#515F74` / `#FFFFFF` | `#B9C7DF` / `#233145` | 次要强调 |
| secondaryContainer / onSecondaryContainer | `#D5E3FC` / `#57657A` | `#3A485B` / `#D5E3FC` | 选择背景及其文字 |
| tertiary / onTertiary | `#00544C` / `#FFFFFF` | `#6BD8CB` / `#003730` | 本地状态 |
| tertiaryContainer / onTertiaryContainer | `#006E65` / `#84F0E2` | `#005049` / `#89F5E7` | 状态强调容器 |
| surface / onSurface | `#FAF8FF` / `#131B2E` | `#10131B` / `#E2E7FF` | 画布、主要文字 |
| surfaceContainerLowest | `#FFFFFF` | `#0B0E16` | 卡片、结果、加载面板 |
| surfaceContainerLow | `#F2F3FF` | `#181C26` | 次级分组背景 |
| surfaceContainer | `#EAEDFF` | `#1E222D` | 嵌入区域 |
| surfaceContainerHigh | `#E2E7FF` | `#282C37` | 较强分组/按键背景 |
| surfaceContainerHighest | `#DAE2FD` | `#333743` | 最高色阶 |
| onSurfaceVariant | `#434654` | `#C3C5D7` | 说明、加载文字 |
| outline / outlineVariant | `#737686` / `#C3C5D7` | `#8D90A1` / `#434654` | 输入框边界、细分隔线 |
| error / onError | `#BA1A1A` / `#FFFFFF` | `#FFB4AB` / `#690005` | 错误及其文字 |
| errorContainer / onErrorContainer | `#FFDAD6` / `#93000A` | `#93000A` / `#FFDAD6` | 错误提示容器 |

浅色来自参考稿；深色是本项目补充的原生扩展，参考稿没有深色截图。当前主题跟随系统，品牌蓝固定；主题手动切换、动态壁纸配色和 AMOLED 模式随设置任务实现。

容器必须搭配对应的 `on*` 文字色；不要在深蓝容器中使用深蓝操作文字。边框默认 1dp，加载卡片使用 `outlineVariant` 的 50% 不透明度；阴影优先 0dp，需浮起时使用 Material 3 elevation。

## 4. 字体、间距与形状

### 字体

原生实现使用 `FontFamily.SansSerif`，由系统提供中文回退；不从 Google Fonts/CDN 下载字体。参考稿的 Plus Jakarta Sans / Inter 用作字重和层级参考。数字结果、编码和技术数据优先使用 `FontFamily.Monospace`，不强制技术字体覆盖中文文案。

| 用途 / Compose 样式 | 字号 / 行高 | 字重 |
| --- | --- | --- |
| 大结果 / displaySmall | 36sp / 44sp | 700 |
| 页面大标题 / headlineLarge | 28sp / 36sp | 600 |
| 页面标题 / headlineMedium、titleLarge | 22sp / 28sp | 600 |
| 分区、工具名 / headlineSmall、titleMedium | 18sp / 24sp | 600 |
| 紧凑标题 / titleSmall | 14sp / 20sp | 600 |
| 正文 / bodyLarge | 16sp / 24sp | 400 |
| 正文、加载提示 / bodyMedium | 14sp / 20sp | 400 |
| 次要说明 / bodySmall | 12sp / 16sp | 400 |
| 操作标签 / labelLarge | 14sp / 20sp | 500 |
| 导航、徽标 / labelMedium、labelSmall | 12sp / 16sp | 500 |

主题样式集中在 `ToolboxTypography.kt`。文本需支持系统字号放大；工具名最多两行，简述最多两行（旧卡片当前三行，随页面迁移调整），加载提示允许换行，重要结果不能截断。

### 间距与形状

采用 8dp 基线，内部细节可使用 4dp 步长。截图的像素尺寸不直接当作 dp。

| 项目 | 统一值 |
| --- | --- |
| 手机页面左右留白 | 16dp |
| 平板页面左右留白 | 24dp |
| 卡片内部留白 | 16dp；紧凑工具项 12dp |
| 同组组件间距 | 8dp / 16dp |
| 分区间距 | 24dp / 32dp |
| extraSmall / small / medium / large / extraLarge | 4dp / 8dp / 12dp / 16dp / 24dp |
| 工具卡片、状态卡片 | large（16dp） |
| 输入框、普通非胶囊按钮 | small / medium（8dp / 12dp） |
| 分组大卡片、底部弹层顶部 | extraLarge（24dp） |
| 分类、状态、选中导航背景 | 胶囊圆角 |
| 普通图标 / 图标底板 | 24dp / 40–48dp |
| 点击区域 | 至少 48 × 48dp，按钮位置与尺寸保持稳定 |

## 5. 页面和组件约定

- **导航**：原生 `Scaffold` + `NavigationBar`，四入口保持位置，原生导航栏内容高度按 80dp 基线并计入系统底部 inset。工具详情采用返回标题栏，顶部标题单行超长省略；返回行为保留来源页的筛选条件。
- **首页**：搜索入口优先，快捷工具与分类位于前部；最近使用和推荐取真实数据。区域标题对齐左侧，次要操作放右侧。
- **目录与搜索**：搜索框/分类置顶，分类可横向滚动；手机常规宽度采用双列工具卡，较宽窗口按可用宽度扩到 3–5 列。窄窗口或大字号应采用单列。列表视图和导航 rail 随独立适配任务实现。
- **工具卡片**：图标 → 工具名 → 说明，星标独立操作且不触发打开工具。背景使用 `surfaceContainerLowest`，可加细边框；名称/数量按实际注册中心与本地状态显示。
- **禁止特性宣传语**：页面不显示“本地处理/无需登录/无需联网”等特性宣传语，工具卡片不显示运行模式徽标；工具名称、描述、加载和错误文案只说明做什么或当前状态，不追加“本地/离线/隐私”等宣传措辞；离线/联网能力由项目规格、工具行为和错误状态体现。
- **收藏**：白色列表卡为目标样式，保留星标、可打开入口和空收藏引导；排序功能有实现后才显示。已有收藏页当前复用网格卡片，布局迁移另行完成。
- **工具详情**：输入、执行、结果操作保持一致；计算器结果采用白色面板。主执行按钮使用 `primary/onPrimary`；数字按键与操作键通过色阶分组。
- **相机预览**：相机类工具在页面内显示预览卡片（当前为竖向 4:3、`FILL_CENTER` 裁剪），叠加居中的识别框与底部半透明提示条；权限被拒、无可用设备和启动失败都在预览位置用说明卡片呈现，并提供重试/关闭操作。不绘制假快门、假闪光或未实现的自动对焦控件；手电筒只在设备确有闪光单元时显示。
- **我的/设置**：白色分组卡片，行结构为图标、标题/说明、值或操作，箭头仅用于可进入的页面。系统默认语言、隐私文案和本地状态需真实反映行为。
- **输入/反馈**：使用原生文本框、焦点/错误样式、Snackbar 或明确错误卡片。错误给出可执行的重试/返回/修改输入入口；未知错误显示通用文案，不把异常堆栈显示给用户。
- **安全区**：消费 `Scaffold` padding 和系统 inset，避免重复加入状态栏高度；键盘出现时输入与主要操作应可滚动。不要在 UI 中绘制假状态栏或导航横条。

### 页面切换动画（统一规范）

所有页面级切换都复用 `core/ui/ToolboxMotion.kt`，页面不得各自实现 enter/exit 动画。

| 场景 | 进入 | 退出 | 时长与缓动 |
| --- | --- | --- | --- |
| 工具详情、搜索打开 | 淡入并从末端滑入约 12.5% 宽度 | 当前页淡出 | 进入 240ms `LinearOutSlowInEasing`；退出 180ms `FastOutLinearInEasing` |
| 从工具/搜索返回 | 底层页淡入 | 当前页淡出并向末端滑出约 12.5% 宽度 | 同上 |
| 底部四个标签互切 | 交叉淡化，无方向位移 | 交叉淡化 | 180ms |

- 首帧启动、对话框、系统弹层和工具页内部状态切换不套用页面动画；工具内部加载继续使用共享 `ToolboxLoading` 组件。
- 不添加弹跳、缩放回弹、持续脉冲或人为延迟；过渡只改变透明度与页面位移，不改变焦点、返回逻辑和 ViewModel/SavedState 行为。
- 沿用 Compose 的 `MotionDurationScale` 与系统动画时长缩放：系统关闭动画时过渡立即完成，不额外实现动画倍率、开关或自定义时长。
- WebView 工具容器使用同一套页面进入/退出规则；网页内部动画由网页负责，不加入全局规范。
- 验收：快速连续打开、返回、切换标签与低端设备上不得闪屏、叠层或重复过渡；关闭系统动画、TalkBack 与横竖屏切换仍需设备验收。

### 网页版工具容器（WebView）

实现位于 core/ui/ToolboxWebView.kt；基础地址来自构建配置 WEB_TOOL_BASE_URL，正式为 https://tool.zhzgo.cn，debug 指向 http://localhost:8088。

- 顶部沿用工具详情返回标题栏，标题显示工具名；内容区为 WebView，不绘制假地址栏或浏览器外壳。
- 首次加载复用 `ToolboxLoadingState` 覆盖内容区；WebView 不可用、无网络、证书错误或加载失败时显示本地化错误卡片，提供重试和返回，不保留空白页面。
- 系统返回键优先回退 WebView 历史，无可回退历史时退出工具；网页内导航限制在白名单域名内。
- 网页版入口或容器需显示“网页版 · 数据将在 tool.zhzgo.cn 处理”的提示，位置由工具任务决定，但不能省略。
- 页面内部按钮、表单和状态由网页实现，需适配移动端宽度、键盘和安全区；颜色不必强行套用原生主题，但不得出现无法关闭的弹窗或不可返回的流程。

## 6. 统一加载效果

参考页面没有完整的加载态。本规范补充以下样式，所有新增 Android 页面必须复用 `core/ui/ToolboxLoading.kt`。

### 首次读取或无内容状态

使用 `ToolboxLoadingState(message)`，作为内容区中的全宽项呈现，不覆盖顶部返回、底部导航或搜索输入。

- 白色/对应深色卡片，16dp 圆角、1dp 细边框，无额外阴影。
- 卡片最小高度 184dp，可随文字放大自然增高；内部 padding 24dp。
- 64dp 圆形淡蓝底：`primary` 的 8% 不透明度。
- 40dp 原生不确定进度环，线宽 3dp，主色 `primary`，轨道为主色的 12% 不透明度。
- 环与提示间距 16dp；文字 `bodyMedium/onSurfaceVariant`，居中、允许换行。
- 使用 Material 3 默认进度动画。不要添加点点点动画、旋转 logo、呼吸缩放、自动弹窗或伪造百分比。

### 局部操作状态

使用 `ToolboxLoadingIndicator(compact = true)`：20dp 进度环、2dp 线宽，同一颜色与动画。放在原操作图标位置或按钮文字前，保持原触控尺寸；仅禁用正在执行的操作，其他工具和导航保持可用。

收藏由已有 `pendingCodes` 驱动：目录、搜索、收藏卡片及工具标题栏的星标写入期间显示小进度环，结束后恢复真实星标；不可用书签的取消按钮同步使用此效果。数据库首次读取期间只有禁用状态的星标不显示写入动画，避免把读取误报为保存。

### 文案与生命周期

| 场景 | 中文资源 | 英语资源 |
| --- | --- | --- |
| 首页/工具目录 | 正在准备工具… | Getting tools ready… |
| 搜索 | 正在搜索… | Searching… |
| 收藏读取 | 正在读取收藏… | Loading favorites… |
| 收藏写入 | 正在保存收藏… | Saving favorites… |

- 加载状态由 ViewModel 提供，UI 不创建自己的请求、计时器或访问 Room/Retrofit。
- 状态实际为 Loading 时显示；成功、空、错误状态立即替换进度环。不要人为延迟结果或设置最低展示时长来让动画出现。
- 当前本地目录/搜索和有界计算很快，加载可能不可见；计算器不添加人为等待。远程请求的超时与取消由 Repository/UseCase/任务状态管理，失败必须退出加载。
- 保留原进度控件的不确定进度语义；加载卡片合并语义并使用 polite live region；收藏按钮保留操作名称并提供「正在保存收藏…」状态描述，不随每一帧播报。
- 动画停止时仍有文字可表达状态。沿用原生动画策略，不另加动画倍率；关闭系统动画、TalkBack 的设备行为仍需验收。
- 已有内容的后续刷新优先保留内容并在所属区域使用紧凑进度；需要列表骨架或真实百分比时作为新任务补充共享组件，不在页面各自实现一套。

## 7. 多语言、可访问性与实现边界

- 默认跟随系统，主要和最终回退语言为简体中文，同时维护英语资源。规则见 [多语言说明](ANDROID_LOCALIZATION.md)。UI 禁止硬编码用户可见文案。
- 图标按钮需有清楚的操作说明；纯装饰图标 `contentDescription = null`。状态同时用文字表达，不只依赖颜色。
- 文字/操作使用对应主题 role；普通正文目标对比度至少 4.5:1，非文本控件目标至少 3:1。主题与半透明叠加后的实际对比度需设备/视觉验收。
- 保持至少 48dp 操作区域，支持键盘、TalkBack 和大字号。当前代码有的自适应网格仍强制至少两列，应在后续页面迁移任务补足窄屏/大字号单列。
- 使用 Compose 原生组件与本地资源，参考 HTML 不打包进 WebView；Tailwind、CDN 字体及 HTML 模拟行为仅用于理解设计。
- 本次没有新增 API、数据库结构、依赖或权限。图形和文案从共享 UI/主题复用，业务状态仍遵循 Compose → ViewModel → UseCase → Repository。

## 8. 本次落地与后续验收

2026-10-04 本次落地范围：

- 建立浅色/深色颜色、字体层级和 shapes 的统一主题。
- 首页、工具目录、搜索和收藏的首次加载改为同一组件。
- 收藏写入在卡片、工具标题栏与不可用书签移除按钮使用紧凑组件。
- 计算器结果面板改为白色/对应深色卡片，使复制/继续计算操作与新主题匹配。
- 提供中文、英语和深色加载的 Compose Preview，便于 Android Studio 预览。

本次不代表五个参考页面已全部重绘。现有首页结构、卡片圆角/留白、收藏网格、我的页与原生计算器按各自路线图任务逐步迁移；手动主题、设置功能和完整科学键盘尚未实现。

使用本机 Android Studio 自带的 JDK 21.0.9 执行 `:app:assembleDebug :app:lintDebug`，最终构建成功（41 秒），Lint 为 0 错误、11 条既有警告，无新增警告。首次构建的 Material 3 字体参数不兼容问题已修正后重新构建；Debug APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`，Lint 报告位于 `android/app/build/reports/lint-results-debug.html`。未新增或运行自动化测试、模拟器或真机验收；Preview 源码已编译，尚未渲染确认效果。

后续 UI 验收应覆盖：浅色/深色、中文/英语、窄屏/平板/横屏、放大字号、返回/切换页、读写失败与重试、快速连续收藏、TalkBack 和关闭系统动画。每次新增页面前阅读本规范，并在对应功能文档记录实际实现及验证范围。
