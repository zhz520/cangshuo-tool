# Android 设计系统（v2 · 蓝色强调）

适用：沧烁工具箱 Android 客户端**所有页面**，含**今后新增的页面与工具界面**。新页面必须遵照本文件，不得自造颜色/圆角/组件。

当前视觉依据为用户提供的 `app-design-10pages.html` 与十页截图；用户已明确取消第 10 页侧边抽屉。历史 Stitch 页面不再作为当前页面布局的验收依据。

视觉基调：极简、轻量、留白充分；以白为主、浅灰为辅、黑色文字，**强调色默认为蓝 `#1677FF`**（设置 → 主题色可切换蓝 / 青绿 / 紫 / 橙 / 玫红 / 黑，仅保存在本机，不参与云同步）。禁止大面积渐变、玻璃拟态、重阴影、彩色工具图标与花哨动画。

## 1. 设计令牌（唯一来源：`ui/theme/`）

所有颜色只能来自 `MaterialTheme.colorScheme.*`，禁止在页面里写十六进制。令牌定义在 `ToolboxColors.kt`。

浅色（默认）：

| 用途 | 令牌 | 值 |
| --- | --- | --- |
| 主背景 | background / surface / surfaceContainerLowest | `#FFFFFF` |
| 次级背景 | surfaceVariant | `#F7F7F8` |
| 工具图标底 | surfaceContainer | `#F3F3F4` |
| 分割线 | outlineVariant | `#EEEEEE` |
| 卡片边框 | outline | `#E5E5E5` |
| 主要文字 | onSurface / onBackground | `#171717` |
| 次要文字 | onSurfaceVariant | `#777777` |
| 强调蓝 | primary | `#1677FF` |
| 选中蓝底 | primaryContainer | `#EAF2FF` |
| 成功 | tertiary | `#16A34A` |
| 错误 | error | `#F04438` |

深色：background `#101011`、surface `#171718`、surfaceContainer `#1F1F22`、文字 `#F5F5F5`/`#9E9EA4`、强调 `#4C9BFF`。深浅色均需适配，默认浅色。

字号（`ToolboxTypography.kt`）：页标题 24 SemiBold、模块标题 20 SemiBold、工具名/列表标题 15 Medium、正文 14、辅助 12。

形状（`ToolboxTheme.kt`）：卡片 `shapes.large`(16dp)、图标容器 16dp、控件/输入 12dp(`shapes.medium`)、分类/标签用胶囊。

间距：4dp 基线；页面左右 16–20dp；网格间距 8/14dp。

## 2. 共享组件（`core/ui/`，必须复用，不要自造）

- `ToolboxTopBar(title, navigationIcon, actions)`：顶栏（标题左对齐，操作在右）。
- `ToolboxIconButton(icon, contentDescription, onClick)`：48dp 原生触控区，内部 22dp 裸线性图标。
- `ToolListItem(title, subtitle, icon, onClick, trailing, showDivider)`：列表行（裸线性图标）。
- `ToolSettingItem(title, value, icon, onClick)`：设置行，正常宽度右侧显示值，窄屏或大字号下移到标题下方；支持禁用与局部加载。
- `ToolGridTile(name, icon, onClick)`：工具网格块（灰底圆角图标容器 + 名称）。
- `ToolIcon(icon, size, iconSize, corner)`：灰底圆角图标容器。
- `ToolSectionHeader(title, trailing)`：分区标题（SemiBold）。
- `ToolboxChevron()`：列表行右侧箭头。
- `ToolGroupCard(title)`：分组卡（白底 + 1dp 细边 + 16dp 圆角），仅用于真正的分组。
- `ToolboxButton` / `ToolboxOutlinedButton` / `ToolboxTextButton`：统一尺寸按钮。
- `ToolboxTextField`：浅灰填充输入框（无边框）。
- `ToolboxSwitch`：共享主题开关，关闭态圆点使用 `onSurfaceVariant`，与浅色轨道区分；沿用原有回调、禁用和无障碍语义。
- `ToolboxDivider(startIndent)`：1dp 细分割线。
- `ToolboxLoadingState` / `ToolboxLoadingIndicator`、`SystemReportCard` / `SystemReportActions`。
- `ToolResultRow(label, value)`：计算结果的标签与数值；窄屏、大字号或长数值上下排布，完整显示结果；明细列表可通过 `ToolListItem.subtitleMaxLines` 展开全文。
- `ToolboxTrendChart(points, description)`：最多 600 点的时间趋势，使用主题强调色与低干扰网格；横轴按真实时间比例，支持平值、单点与可访问描述。
- `ToolboxAvatar`：圆形 QQ 图片、加载环或默认用户图标；不直接请求网络。
- `ToolboxScaffold`：统一顶栏、安全区、键盘避让与内容最大宽度；`ToolboxMotion` 提供页面及底栏过渡。

## 3. 图标

统一线性风格（24dp 画布、1.8 描边、圆头圆角，靠 `Icon` 的 tint 着色）。

- **灰底圆角图标容器**：**仅**用于工具网格（首页“常用工具”、工具库网格）。
- **列表行与菜单**：使用**裸线性图标**（不加灰底），与侧边菜单一致。
- 新增图标放进 `res/drawable`，保持同一线性规格。

## 4. 页面骨架

- 底栏四入口：首页 / 工具库 / 收藏 / 我的（`NavigationBar`，选中为蓝色，顶部 1dp 分割线）。
- 首页顶栏：标题 + 账号/设置入口；**不使用侧边菜单或抽屉**。业务入口保留在底栏和“我的”列表。
- 工具库：顶栏 + 搜索 + **左侧分类栏**（选中蓝色文字 + 左侧蓝色指示条）+ 右侧 3 列网格。
- 详情页顶栏：返回 + 工具名 + 收藏星标（+ 真实存在的更多操作）。
- 全屏子页（最近使用、设置、账号、反馈）：返回顶栏 + 内容。
- 账号与设置内容最大宽度 `ToolboxDimens.formMaxWidth`（560dp），左右留白 16dp；表单与选择弹窗可随键盘和字号滚动。
- 设置页以“外观与启动 / 通用”及细分割线组织，不叠加大卡片；登录/资料/QQ 头像和同步设置沿用同一输入框、按钮与文字层级。
- QQ 头像地址为 `https://q.qlogo.cn/headimg_dl?dst_uin=<QQ号>&spec=640&img_type=jpg`，从数字 QQ 邮箱识别或手动填写；空值保存表示使用默认头像。失败提示与保存失败分开，显示真实加载状态，切换账号取消旧请求。

## 5. 规则

1. 颜色/圆角/字体一律用主题令牌；禁止硬编码颜色与 `RoundedCornerShape(数字)`。
2. 文案一律走 `strings.xml`（中英对齐），禁止硬编码字面量。
3. 新页面优先复用上面的共享组件；确需新组件时放进 `core/ui/` 并在此登记。
4. 交互一致：Loading/Empty/Error 用共享组件；点击/选中/禁用状态清晰。
5. **未实现的功能必须显式提示**（如 `feature_unavailable_*` 文案），不得假装成功。
6. 深浅色都要适配；正确处理状态栏、导航栏与 Insets。

2026-10-12 开关适配依据：实际查阅 [AndroidX SwitchTokens](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/SwitchTokens.kt)，blob `1fd474ec436e63b0ec54c455f32445d2d4ef5123`。默认未选中圆点使用 `outline`、轨道使用 `surfaceContainerHighest`，本主题两者均为 `#E5E5E5`，模拟器中圆点不可辨。共享包装只调整主题角色，保留 Material3 触摸、动画及状态逻辑；房贷明细开关整行可切换并合并文字标签。分支链接可变化，未复制上游源码。修复后在模拟器（Android 16，1.5 倍字体、840dp 宽、深色与浅色）复核：未选中圆点清晰可辨，明细开关整行点击可开可关。

参考实现：`feature/home/ui/HomeScreen.kt`（骨架 + 首页 + 工具库 + 我的）、`HomeRoute.kt`（全屏子页与路由）、`feature/recent/ui/RecentScreen.kt`、`feature/settings/ui/SettingsScreen.kt`、`feature/auth/ui/AuthScreen.kt`。实现和验收记录见 [账号与设置 UI](ANDROID_ACCOUNT_SETTINGS_UI.md)。
