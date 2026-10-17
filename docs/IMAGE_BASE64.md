# 图片 Base64（image_base64）

唯一编码 `image_base64`，分类 `IMAGE`，模式 `LOCAL`，版本 1，排序 106，无需登录且不进入推荐位。它把图片文件字节转换为标准 Base64 或 Data URL，也能把 Base64/Data URL 解码为 PNG、JPEG、WebP 或 GIF 预览；提供复制、保存文本和分享文本。它与已有的文本 `base64` 工具分开登记，避免改变文本编解码器的严格 UTF-8/Hex 语义。

Android 客户端使用 Compose → ViewModel → UseCase → Repository 分层；V59 仅登记目录元数据，没有新增接口、表结构、权限或依赖。文本输入、图片字节和导出文本都不上传服务端。

## 目录契约

| 字段 | 中文 | English |
| --- | --- | --- |
| name | 图片 Base64 | Image Base64 |
| description | 图片与 Base64/Data URL 双向转换，预览、复制并保存分享文本。 | Convert images to Base64 or Data URLs and preview decoded Base64 images. |
| keywords | 图片、Base64、Data URL、编码、解码、image、data url、base64 image | image、base64、data url、encode、decode、picture |
| category / mode | IMAGE / LOCAL | IMAGE / LOCAL |
| icon / sort | image_base64 / 106 | image_base64 / 106 |
| login / featured / status / version | false / false / ENABLED / 1 | false / false / ENABLED / 1 |

[V59 种子](../server/src/main/resources/db/migration/V59__seed_image_base64_tool.sql) 只插入目录列，分类通过 `IMAGE` code 查询。`ON DUPLICATE KEY UPDATE tool_code = tool_code` 使迁移重放不覆盖后台名称、说明、关键词、配置、状态、时间戳或软删除结果。

## 参考与取舍

2026-10-17 实际阅读了以下公开源码：

| 来源 | 实际采用与适配差异 |
| --- | --- |
| [DevToys `Base64ImageDataTypeDetector.cs`](https://github.com/DevToys-app/DevToys/blob/main/src/app/dev/DevToys.Blazor/BuiltInDataTypeDetectors/Base64ImageDataTypeDetector.cs)，blob `0c9b73c0e71e9a81f75d7894c692ef956b0f1baf` | 上游先 trim 输入，再按不区分大小写的前缀识别 PNG、JPEG、BMP、GIF、ICO、SVG 和 WebP Data URL；它只检查声明前缀，不检查解码字节。这里采用 Data URL 前缀与 trim 思路，但限定 PNG/JPEG/WebP/GIF，并在前缀之后检查 magic bytes。分支链接可变，blob 是文件哈希而非 commit；没有复制上游代码或引入 DevToys 依赖。 |
| [IT-Tools `base64-file-converter.vue`](https://github.com/CorentinTh/it-tools/blob/main/src/tools/base64-file-converter/base64-file-converter.vue)，blob `a489f9a1de61ab1bbbe5bf80dc822e5b95e07c44` | 上游把文件按 Base64 字节处理，输入通过校验后才允许预览或下载；图片预览宽度不超过 100%，高度不超过 400px。这里沿用“文件字节优先、校验通过才展示”的流程，以及限制预览尺寸的思路；保存和分享按本项目 Android 文本工作流实现。未复制组件代码。 |
| [IT-Tools `base64.ts`](https://github.com/CorentinTh/it-tools/blob/main/src/utils/base64.ts)，blob `44e59f41f5c261e54f92e9791ab04fc09dd1e295` | 上游会剥离 Data URL/MIME 前缀，通过解码再编码检查 Base64，并处理 URL-safe 映射与填充。这里采用前缀剥离、标准/URL-safe 识别和规范输出思路；额外把解码预算放在解码前检查，并用 magic bytes 决定真实图片类型。未复制 `js-base64` 依赖或该实现。 |

这些参考确认了三种关键行为：Base64 代表文件字节而不是文本；预览必须以有效解码为前提；显示尺寸应有明确上限。本项目另外加入了 Data URL 声明与文件内容一致性校验，以及 Android 内存预算。

## 双向转换

编码方向接受系统照片/文件选择器返回的单个图片，读取原文件字节后生成标准 Base64。默认输出 `data:<mime>;base64,<payload>`；关闭 Data URL 后可选 URL-safe 字符和每 76 字符换行。Data URL 始终使用标准字符和单行，避免把普通换行写进 URL 参数。编码不重新压缩、旋转或清除元数据，复制的是原文件字节对应的完整结果。

解码方向接受带前缀的 Data URL 或不带前缀的 Base64。只识别以下四类图片：

- `data:image/png;base64,...`
- `data:image/jpeg;base64,...`；`image/jpg` 仅作为 JPEG 别名归一化
- `data:image/webp;base64,...`
- `data:image/gif;base64,...`

MIME 和 `base64` 标记按不区分大小写处理。只接受 `;base64` 传输形式；BMP、ICO、SVG、AVIF、HEIF、percent-encoded Data URL 和其他格式不在支持范围。动画 GIF 只预览首帧，不展示所有帧。

## 格式识别与资源边界

解码得到的字节必须先通过 magic bytes 识别，声明 MIME 只用于一致性检查；原始 Base64 没有声明类型时也由字节识别。支持的头信息如下：

| 类型 | magic bytes |
| --- | --- |
| PNG | `89 50 4E 47 0D 0A 1A 0A` |
| JPEG | `FF D8 FF` |
| WebP | `52 49 46 46`（RIFF）… `57 45 42 50`（WEBP，偏移 8） |
| GIF | `47 49 46 38 37 61` 或 `47 49 46 38 39 61`（GIF87a / GIF89a） |

Data URL 声明为 PNG 但字节实际是 JPEG，或声明为支持格式但字节无法识别时，停止预览并显示类型不一致/不支持错误。不能只凭 MIME 或文件扩展名信任内容。

| 资源 | 上限与处理 |
| --- | --- |
| 二进制输入 | 4 MiB；图片读取和 Base64 解码都在展开前估算并随时检查预算，为完整 Base64/Data URL 字符串和预览位图保留堆空间 |
| 文本输入 | 6,000,000 个字符；Data URL 头在逗号前最多 256 个字符 |
| 源图片尺寸 | 每边最多 100,000 像素，总像素不超过 500,000,000 |
| 预览 | 最长边最多 1,600 像素；预算为 `min(64 MiB, heap / 4)`，按 RGBA_F16 最坏情况 8 字节/像素并计入旋转副本和已保留的字节/文本内存 |
| 文本展示 | 界面只显示前 1,024 个字符并提示截断；复制、保存和分享使用完整结果 |

尺寸、内存或字符数越界均给出本地化错误，不把截断内容当作完整结果。读取、转换与分享文件写入在 IO 调度器执行，Repository 用互斥保护原生工作；取消在读取块、解码前后和文本写入块检查，旧任务不得覆盖新输入。失败或取消时清理未交付的临时分享文件。

## 界面与功能范围

- 图片 → Base64 与 Base64 → 图片双向模式；有加载、空、错误、取消、重试和结果状态。
- Data URL、URL-safe、76 字符换行选项；Data URL 开启时后两项不可用。
- 展示识别类型、原图/预览尺寸、原始字节数、输出字符数和缩小提示。
- 预览使用内存内有界采样和 EXIF 方向纠正；不把完整大图直接解码到屏幕。
- 复制完整文本，通过系统文档选择器保存 UTF-8 文本，通过 FileProvider 分享 `.txt` 副本。
- 不保存到公开相册，不下载图片，不提供批量文件、任意二进制、动画帧拆解或图片格式重编码。

Android 端 UI 不直接解码图片、访问 Retrofit 或 Room。静态检查要求 `ImageBase64ToolDefinition` 使用共享的中英资源，分类 `IMAGE`、模式 `LOCAL`、排序 106；bundled ToolRegistry 只注册一次，首页为 `image_base64` 映射唯一的 `ic_image_base64` 24dp 矢量图标，且不声明额外运行时权限。

## 静态与隔离验收

离线静态命令：

```powershell
python -B scripts/check_image_base64_runtime.py --static
```

检查器复用 `check_color_picker_runtime.py` 的目录 DTO/静态辅助和 `check_admin_statistics_runtime.run` 的隔离 Docker/MySQL 入口。静态模式检查 V59 列与精确元数据、中文关键词顺序、IMAGE 分类查询、重复键 no-op；解析两套 `strings.xml`，核对 name/description/keywords key 集及格式占位符；再检查 ToolDefinition 字段、元数据默认值、无额外权限、唯一 code owner、恰好一次注册、唯一图标映射和非空 24dp 向量路径。

候选镜像模式必须在具备隔离容器和专用 MySQL 的部署主机运行，会创建唯一临时数据库、用户和容器并在 `finally` 清理：

```powershell
python -B scripts/check_image_base64_runtime.py <candidate-image>
```

该模式验证 V59 已执行、公开目录与后台详情/筛选字段、后台编辑后迁移重放不改数据、工具使用计数及 7 天排名。它仍不替代 Android 图片解码、预览、UI、保存/分享和真机验收。

## 实际验证状态

2026-10-17 执行结果：

- `python -B scripts/check_image_base64_runtime.py --static`：**42/42 通过**，核对 V59、双语 key/占位符、唯一定义/注册/图标和无额外权限。
- `python -B -m py_compile scripts/check_image_base64_runtime.py`：命令退出码 0。
- 服务端完整 Maven 测试 **226/226**；候选镜像 `sha256:369b36726dafea3ad21b37a594f5dcfd7ca92dbcbdd9b63017b78db4e0563662`，真实隔离 MySQL/API 候选 **185/185**（V59、公开/后台 DTO、迁移重放、编辑/状态/软删除、热门、每日/累计与统计），临时资源已清理。
- 新增 12 项 JVM 回归：magic bytes、Data URL/裸 Base64/空白/URL-safe/填充、声明 MIME 不一致、输入与二进制预算、Data URL/URL-safe/76 字符换行、RGBA_F16/旋转预留、模式切换、输入上限、取消选择，以及加载中修改选项不得让旧任务失去结果归属。
- 完整 Android `testDebugUnitTest`：**572/572**，0 失败/0 错误/0 跳过；`assembleDebug` 通过，Lint **0 错误 / 81 既有警告**，新增源码与资源无发现。
- Android 16 模拟器通过系统照片选择器加载 138,683 字节 PNG，显示 `image/png`、原图 1080 × 2400、预览 540 × 1200 和完整的 `data:image/png;base64,` 输出，共 184,934 字符；复制后出现系统剪贴板浮层。
- 同一模拟器以 91 字符 URL-safe、无填充的 1 × 1 PNG 输入成功解码，界面显示 68 原始字节、`image/png`、原图/预览 1 × 1，并规范化为带填充的标准 Data URL；关闭 Data URL、开启 URL-safe 和 76 字符换行后输出即时更新为 92 字符且实际换行。
- 保存流程通过系统文件选择器写入 Downloads 的 `decoded.png.base64.txt`，回读 114 字符，前缀与结尾和界面完整结果一致；分享流程通过 FileProvider 生成 `.txt` 并打开系统分享面板。
- 静态脚本与完整 Android 门禁已通过；服务端候选、真实 Flyway/API 和生产发布结果由本轮验收记录和 ROADMAP 回填，未把候选检查等同于已部署。

独立复核又收紧了资源与并发边界：二进制上限调整为 4 MiB、文本上限 6,000,000 字符；换行改为单缓冲构造，避免分块列表；渲染和 Repository 捕获 OOM；选项重算期间禁止复制/保存/分享；换图、清空和模式切换取消旧保存/分享任务；超长粘贴明确报错且禁止继续解码旧内容；解析、Base64 解码与图片解码前后增加取消检查。同步等待底层 bitmap codec 仍不能被强制中止，这是 Android 原生调用边界。

尚未覆盖 JPEG、WebP、动画 GIF 的真机样例，以及接近 4 MiB 的极限输入、低内存设备、TalkBack、不同文件 provider 与横屏大字号；这些边界由领域测试和代码预算覆盖，未经真实设备逐项确认。服务端目录仍需候选镜像验证迁移/API 后再发布。
