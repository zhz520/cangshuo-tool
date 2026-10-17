# 手持弹幕

## 定位

- 工具编码：`handheld_banner`
- 分类 / 模式：`LIFE` / `LOCAL`
- 排序：625
- 数据迁移：`V73__seed_handheld_banner_tool.sql`
- 分层：Compose → `BannerViewModel` → `ApplyBannerSettingsUseCase` → `BannerSettingsRepository`

将单行文字显示为持续横向滚动的全屏弹幕，适合接机、门店提示、后台应援和临时告示。文字与样式只保存在本机，不联网、不登录、不申请额外权限。

## 开源参考

查阅日期：2026-10-17。

| 项目 | 固定版本与许可 | 实际核验源码 | 采用内容与适配差异 |
| --- | --- | --- | --- |
| [ronghao/AutoScrollTextView](https://github.com/ronghao/AutoScrollTextView) | `b6702a0096f33f77953a7e3bb2917a2683c0d1f1`；Apache-2.0 | [MarqueeTextView.java](https://github.com/ronghao/AutoScrollTextView/blob/b6702a0096f33f77953a7e3bb2917a2683c0d1f1/library/src/main/java/com/haohaohu/autoscrolltextview/MarqueeTextView.java)、[BaseScrollTextView.java](https://github.com/ronghao/AutoScrollTextView/blob/b6702a0096f33f77953a7e3bb2917a2683c0d1f1/library/src/main/java/com/haohaohu/autoscrolltextview/BaseScrollTextView.java) | 参考单行文字宽度测量、按速度累计位移、循环重置和暂停停止方案。本项目改用 Compose 的 `TextMeasurer`、Canvas 与 `withFrameNanos`，以帧时间差计算位移，避免固定毫秒任务与主线程消息堆积；支持双向滚动、镜像和多份文字连续铺满视口。 |
| [sergiomattioli/LedMagic](https://github.com/sergiomattioli/LedMagic) | `ab7dfe7cf78a6922a5bf52d6b28a1f27dc3b9830`；MIT | [MainActivity.java](https://github.com/sergiomattioli/LedMagic/blob/ab7dfe7cf78a6922a5bf52d6b28a1f27dc3b9830/LedMagic/src/com/astro/ledmagic/MainActivity.java)、[FullScreenActivity.java](https://github.com/sergiomattioli/LedMagic/blob/ab7dfe7cf78a6922a5bf52d6b28a1f27dc3b9830/LedMagic/src/com/astro/ledmagic/FullScreenActivity.java) | 参考“先编辑文字与样式，再进入独立全屏展示”的流程及全屏视觉优先原则。本项目未复制其 LED 点阵图片、7 段字库、固定颜色资源或旧式 Activity 实现，改用 Compose 字体与主题令牌。 |

两个参考项目均未作为依赖引入，也未复制源文件。

## 已实现功能

- 单行文字实时预览；输入后立即在本页滚动，不使用伪造加载状态。
- 全屏播放，隐藏系统状态栏和导航栏；返回键、退出按钮均可结束。
- 控制面板进入全屏后显示，运行中的 4 秒无交互后自动淡出；点按屏幕可重新显示或隐藏。
- 开始、暂停、继续；暂停保留当前像素位置，继续从原位滚动。
- 20–240 `dp/s` 连续速度调节，界面显示“慢速 / 标准速度 / 快速”语义。
- 32–140 `sp` 字号调节，界面显示“小字 / 标准字号 / 大字”语义。
- 从右到左、从左到右两种滚动方向。
- 镜像显示，可将屏幕朝向玻璃反射给另一侧的人阅读。
- 全屏保持屏幕常亮；退出全屏立即释放窗口标志。
- 欢迎光临、接机送站、生日快乐、借过一下四个中英文快捷文案。
- 清空文字、恢复默认样式；文字与样式写入 `SavedStateHandle`。
- 中文和英文完整资源，无硬编码颜色、圆角或业务文案。

## 输入与渲染规则

- 文字最多 120 个 Unicode 码点；输入阶段保留首尾空格，粘贴换行、回车和制表符压成空格。达到上限时会回退到完整字素簇边界，不把组合 emoji、旗帜或肤色修饰符截成半个字符。
- ISO 控制字符和其他不可见格式字符会过滤；零宽连接符和零宽非连接符保留，以免拆散组合 emoji。
- 仅含空白或连接符的内容不可播放；清空后预览显示说明。
- 速度限制为 20–240 `dp/s`，字号限制为 32–140 `sp`；恢复保存状态时再次收敛到边界。
- 排版使用单行、粗体、等距字距，文字宽度由 Compose `TextMeasurer` 实测，不依赖字符数估算。
- 文字之间固定留出 56dp 空隙；按当前滚动方向计算需要绘制的副本位置，最多绘制 128 份，避免极端几何参数造成失控循环。
- 镜像作用于完整滚动画面；这与将手机朝向玻璃时的真实反射一致。
- 动画步长单帧最多计入 100ms；应用退到后台再回来时不会把整段后台时间折算成突然跳跃。

## 全屏与颜色

- 全屏背景在浅色主题使用 `inverseSurface`，深色主题使用 `surfaceContainerLowest`，保持 LED 场景所需的深色底。
- 前景优先使用当前主题强调色；若强调色与背景对比度低于 3:1（例如浅色主题的纯黑强调色），回退到主题 `tertiary`。
- 主页面预览会按系统字体缩放限制实际渲染高度，避免 2 倍字体和大字号把固定预览面板裁切；全屏播放仍使用用户选择的完整字号。
- 控制系统使用普通主题表面和文本令牌；不引入玻璃拟态、霓虹发光、闪烁或频闪效果。
- 滚动只用稳定平移，不采用突发爆闪或高频反差动画；用户可随时暂停和退出。
- 全屏窗口获得焦点时隐藏系统栏，关闭时恢复原系统栏行为；不强制横屏，用户可自行横握设备。全屏表面提供可访问的“显示或隐藏控制”点击动作，TalkBack 双击也能在控制面板隐藏后重新显示暂停和退出按钮。

## 边界

- 不访问设备 LED、红外、相机、麦克风或通知权限；不提供息屏后持续播放或后台前台服务。
- 不生成视频或 GIF，不支持多行、图文混排、动画字体、表情雪花和声音。
- 暂停位置只保留在当前全屏会话；退出后重新进入从起点滚动。文字和样式本身会恢复。
- 不支持屏幕亮度调节、方向锁定、防烧屏位移或第二屏幕输出。
- 复杂文字排版的字形宽度由系统字体决定；不同 ROM 的字形、换行和 emoji 外观可能不同。镜像出于展示用途，不改变复制文本或读屏内容。

## 验证范围

定向 JUnit 覆盖：

- 换行/制表符压平、控制字符过滤、组合 emoji 保留、120 码点截断。
- 空白与不可见字符校验、速度/字号范围、设置归一化。
- 左右两种起始边、进度取模、可见副本覆盖、非法几何参数。
- ViewModel 输入、保存、样式恢复、镜像/方向/常亮开关、清空和默认样式。

2026-10-17 Android 16 模拟器（1080×2424）实测：搜索关键词可打开手持弹幕；40 余字输入、快捷文案、实时蓝黑预览、速度/字号滑块、左右方向切换和镜像翻转正常；全屏后系统栏隐藏，4 秒后控制面板自动隐藏，点按恢复；暂停按钮切换为“继续”，退出后系统栏和常亮标志恢复。系统 2 倍字体下，输入、快捷文案、滑块、方向和镜像区域均可读并按宽度换行。真机、不同 ROM 字体、TalkBack 全流程与低端设备帧率仍未验收。

最终本地门禁：Android 全量 **675/675**、`assembleDebug` 成功、Lint **0 错误 / 85 警告**，新增手持弹幕源码与资源无 finding；服务端沿用 **226/226** 基线；V73 静态契约 **52/52**。APK 为 **23,688,701** 字节。

2026-10-17 发布验收：严格隔离候选 **227/227** 通过，使用一次性 Docker 网络，仅连接生产 MySQL 容器与临时 Redis；未接入生产 edge/backend 应用网络，未继承第三方凭据。生产只读 HTTPS 验收 **124/124**；生产镜像 `sha256:bf6b4f6cd8775c04126c70b7ecd770031124cd0cd1e5203630b54d2aa9aa4c5e`，Git 回滚标签 `before-handheld-banner-v73`，上一镜像保留为 Docker 标签 `cangshuo-toolbox-production-api:before-handheld-banner-v73`。Flyway 为 **73/73** 且全部成功，五个容器 healthy，官网与后台登录页均返回 200，一次性和临时网络已清理。
