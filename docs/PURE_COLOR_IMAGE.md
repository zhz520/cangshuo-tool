# 纯色图制作（pure_color_image）

## 定位与当前范围

`pure_color_image` 是图片分类下的本地工具：用户选择颜色与宽高，预览透明或纯色效果，然后生成 PNG 并通过系统流程保存或分享。服务端只提供工具目录元数据，不接收颜色、尺寸或生成图片。

| 字段 | 约定 |
| --- | --- |
| code | `pure_color_image` |
| 中文名 / 英文名 | 纯色图制作 / Pure Colour Image |
| 中文说明 | 选择颜色与尺寸，生成透明或纯色 PNG，并保存或分享。 |
| 英文说明 | Create a solid or transparent PNG at a custom size, then save or share it. |
| 分类 / 模式 | `IMAGE` / `LOCAL` |
| 图标 | `pure_color_image` |
| 排序 / 版本 | 107 / 1 |
| 登录 / 推荐 | 不需要登录 / 非推荐 |
| 中文关键词 | 纯色、纯色图、颜色、背景图、PNG、生成、图片 |
| 英文关键词 | solid color、pure color、background、image、png、generate、blank |

Android 已实现 Compose → ViewModel → UseCase → Repository 分层，位于 `feature/purecolor`；V60 仅提供目录元数据，颜色值、尺寸和 PNG 不上传服务端。

## 目录种子与重放

[V60__seed_pure_color_image_tool.sql](../server/src/main/resources/db/migration/V60__seed_pure_color_image_tool.sql) 只插入现有 `tool_definition` 列：中文名称与说明、`IMAGE` 分类查询、纯色图图标、7 个有序中文关键词、`LOCAL`、非登录、启用、版本 1、排序 107、非推荐。它不新增表、列、API、配置 JSON 或服务端图片处理能力。

末尾使用 `ON DUPLICATE KEY UPDATE tool_code = tool_code` 作为无操作重放。若目录条目已存在，迁移重放不会覆盖后台编辑的名称、说明、图标、关键词、版本、排序、登录/推荐配置、状态、配置 JSON、时间戳或软删除结果。英文名称、说明与关键词由 Android 的 `values-en` 资源维护，V60 只保存当前目录表的中文展示字段。

## 开源参考

2026-10-17 实际阅读了 `T8RIN/ImageToolbox` master 源码。检索和读取时 master 对应提交 `e0c91fa856886bd5465212b6605952207ca10ff6`；以下链接固定到该提交，避免分支继续变化后失去可复核内容。项目采用 Apache License 2.0，本工具只借鉴流程与边界，没有复制文件或引入其依赖。

| 来源 | 采用内容与适配差异 |
| --- | --- |
| [ImageToolbox `ColorImageExporter.kt`](https://github.com/T8RIN/ImageToolbox/blob/e0c91fa856886bd5465212b6605952207ca10ff6/core/ui/src/main/kotlin/com/t8rin/imagetoolbox/core/ui/utils/ColorImageExporter.kt)，文件 blob `2389c1b840ca15f83f1fe5249b5aaf7b6edc6d6c` | 上游以 `MAX_SIZE = 4096` 限制宽高，保存前要求两边都在 `1..MAX_SIZE`；创建位图后用 `eraseColor` 填充整幅颜色，再按无损 PNG 编码，结束时回收位图并检查协程状态；保存可通过一次性位置 URI 进入外部保存流程。这里采用每边 1..4096、先创建再整幅填色、PNG、位图回收、取消检查和外部保存思路。当前 Android 数据层用 `Canvas.drawColor` 完成同样的全画布填充，并额外限制总像素、内存与输出字节。 |
| [ImageToolbox `ColorImageExportDialog.kt`](https://github.com/T8RIN/ImageToolbox/blob/e0c91fa856886bd5465212b6605952207ca10ff6/core/ui/src/main/kotlin/com/t8rin/imagetoolbox/core/ui/widget/dialogs/ColorImageExportDialog.kt)，文件 blob `e21b67d29631706dc3b3a67a61a21774389586ef` | 上游用可保存的宽高状态、`1..4096` 的保存条件、保存期间禁用操作、加载/取消和自定义保存位置；颜色缩略图使用透明棋盘背景，便于识别 Alpha。这里采用透明预览、尺寸有效性门控、保存中状态和系统保存位置思路；默认尺寸改为 1080×1080，并增加总像素错误、分享入口和本地化状态。 |

上游还支持颜色名称解析，而本项目当前只接受十六进制颜色。上游组件、Hilt 注入、文件控制器和图片编码器没有进入本项目依赖或代码。

## 功能与边界

- 颜色输入支持 `#RGB`、`#RRGGBB`、`#RRGGBBAA`，`#` 可省略；统一转为 Android ARGB，文件名和界面按 CSS `#RRGGBB[AA]` 显示。空值、非十六进制字符和长度不符均视为无效；首尾空格会被忽略。
- 宽高必须为 1..4096 的整数，且 `width × height` 不得超过 8,000,000 像素。前者保留上游的每边上限，后者用于约束高分辨率正方形及接近 4096×4096 的分配。
- 编码前还要满足位图与工作副本的堆预算：约为 `width × height × 4 + 8 MiB`，且不得超过 96 MiB 与最大堆三分之一中的较小值。即使总像素未超出 800 万，低内存设备也可能拒绝生成并显示内存错误。
- PNG 字节写入上限为 32 MiB。只生成 PNG，不输出 JPEG、WebP、SVG、渐变、纹理或批量图片；PNG 元数据不包含用户输入历史。
- Alpha 小于 1 时，界面用棋盘格展示透明区域；棋盘格只属于预览，不写入导出 PNG。零 Alpha 的全透明颜色生成透明画布。
- Android 10 及以上保存到系统图片集合的 `Pictures/CangshuoToolbox` 相对目录；旧系统或无法直接写入时使用系统文档创建器。分享使用 FileProvider 生成临时 PNG，并在 24 小时后清理旧分享文件。
- 文件名采用 `pure-color-#RRGGBB[AA]-WIDTHxHEIGHT.png`，例如不透明蓝 `pure-color-#1677FF-1080x1080.png`，半透明蓝 `pure-color-#1677FF80-1080x1080.png`。
- 保存、分享或图片生成失败时结束忙碌状态并显示本地化错误；取消不会伪装成成功。生成、保存和分享串行执行，失败或取消时清理未交付文件。

尺寸校验发生在位图分配前；静态检查无法验证真实分配、原生压缩耗时、分享接收方行为或低内存设备表现。

## 多语言与 Android 静态契约

Android 元数据使用 `pure_color_name`、`pure_color_description` 和 `pure_color_keywords`，其余 `pure_color_*` 键覆盖颜色、尺寸、预览、保存、分享和错误状态。中文资源是回退文案，英文资源提供同一键集合；本说明与检查器固定核对以下内容：

- 中文名称、说明和 7 个有序关键词与 V60 完全一致。
- 英文名称 `Pure Colour Image`、英文说明和 7 个有序关键词与约定一致。
- 两套 `pure_color_*` 字符串键集合相同，每个被 `feature/purecolor` 引用的字符串在两个语言中都存在且非空。
- 每种语言中的格式占位符集合一致，例如尺寸提示的两个整数占位符和尺寸值的两个字符串占位符。
- `PureColorToolDefinition` 是 `code = "pure_color_image"` 的唯一定义；分类 `IMAGE`、模式 `LOCAL`、图标 `pure_color_image`、排序 107、非推荐，并采用元数据默认的无登录、启用、版本 1。
- bundled ToolRegistry 恰好注册 `PureColorToolDefinition` 一次，首页恰好映射一次 `ic_pure_color_image`，图标资源是含非空路径的 24dp 矢量图，工具定义不声明额外运行时权限。

## 静态验收

离线命令：

```powershell
python -B scripts/check_pure_color_image_runtime.py --static
```

2026-10-17 结果：**45/45 通过，0 失败，退出码 0**。检查范围包含 V60、双语资源的键/占位符/固定文案、唯一 ToolDefinition、唯一注册、图标映射与图标资源；未访问网络、Docker 或数据库。

- 完整 Android `testDebugUnitTest` **580/580**，0 失败/错误/跳过；`assembleDebug` 通过，Lint **0 错误 / 81 既有警告**，本工具新增文件无 Lint 发现。
- Android 16 模拟器实测 `#1677FF80`、1080×1080 的界面棋盘格提示和文件名 `pure-color-#1677FF80-1080x1080.png`；保存到 `Pictures/CangshuoToolbox` 后回读 PNG 为 1080×1080 RGBA，中心与角落像素均为 `(22,120,255,128)`。系统分享面板成功显示图片缩略图。

真机、TalkBack、低内存设备、API 26–28 文档选择器和所有分享接收方仍未逐项验证。

## 后续 Android 验收

Android 集成任务需要单独完成并通过以下检查后，才能声明客户端功能完成：

1. 编译、单元测试与 Lint 通过，ToolRegistry 和首页只会出现一个纯色图入口。
2. 在 Android 16 模拟器或真机读取导出的 PNG 像素与 Alpha，覆盖 1×1、透明色、普通颜色、每边 4096 的单行/单列、总像素恰好 8,000,000 和超过上限。
3. 验证 `#RGB/#RRGGBB/#RRGGBBAA` 的有效与无效输入、空格、极值、键盘输入和状态恢复。
4. 验证 API 29+ 的 Pictures 保存、旧系统的系统文档保存、取消保存、分享面板、FileProvider 授权和旧分享文件清理。
5. 检查中文、英文、深浅色、大字体、TalkBack、透明棋盘格以及生成期间取消/返回行为。

## 后续候选与发布

候选镜像模式复用 `check_admin_statistics_runtime.run` 创建隔离数据库、受限用户与临时容器，并在 `finally` 清理：

```powershell
python -B scripts/check_pure_color_image_runtime.py <candidate-image>
```

该模式预期验证 V60 成功执行、目录中只有一条 `pure_color_image`、公开/后台 DTO 字段一致、IMAGE 筛选和编辑可用、迁移重放保留配置文件与时间戳、使用计数和 7 天统计可参与排名。本轮**未构建候选镜像，也未运行该模式**。

服务端完整 Maven 测试 **226/226**。在用户明确授权后，V60 候选镜像 `sha256:f0bb74580be3a9802c09f24f1454f87099b1d6f5884d68745167e451ad1cea13` 完成真实隔离 MySQL/API **188/188**；首次因临时容器启动超时未就绪，原样重试通过。

提交 `1b60f6d` 已推送并由部署仓库同步；生产 API 直接推广同一候选镜像，回滚标签 `before-pure-color-v60`。Flyway **60/60、失败 0**，`pure_color_image` 为 `ENABLED / LOCAL / sort 107`；线上 HTTPS 只读验收 **117/117**，五个容器 healthy。
