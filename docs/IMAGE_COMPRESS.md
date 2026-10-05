# 图片压缩与处理工具

工具编码 `image_compress`，分类 `IMAGE`，模式 `LOCAL`。提供单张图片的压缩、等比缩放、格式转换、前后预览、大小对比、保存和分享。应用不向服务端上传图片；工具实现无需登录或网络 API。

## 开源参考与适配

查阅日期：2026-10-04。以下为实际查阅的分支源码链接，会随上游更新变化；本轮未核实固定 commit。

| 来源 | 阅读与采用思路 | 本项目适配差异 |
| --- | --- | --- |
| [Compressor / Compressor.kt](https://github.com/zetbaitsu/Compressor/blob/master/compressor/src/main/java/id/zelory/compressor/Compressor.kt) | 在后台执行压缩，以缓存文件承载结果，按约束组织处理 | 使用现有 Repository 与 UseCase；原生图片操作串行执行，取消不能导致两个高内存任务同时运行 |
| [Compressor / ResolutionConstraint.kt](https://github.com/zetbaitsu/Compressor/blob/master/compressor/src/main/java/id/zelory/compressor/constraint/ResolutionConstraint.kt) | 先读尺寸，再根据目标约束处理 | 增加输出像素、边长和内存预算检查；不直接沿用上游约束满足条件 |
| [Compressor / Util.kt](https://github.com/zetbaitsu/Compressor/blob/master/compressor/src/main/java/id/zelory/compressor/Util.kt) | 采样解码、方向校正、直接向文件编码 | 预览单独按最长边采样，覆盖狭长图片；增加镜像方向、白色 JPEG 背景、真实输出预览和失败产物清理 |
| [Android 大图加载说明](https://developer.android.com/topic/performance/graphics/load-bitmap) | 尺寸探测与采样解码的官方语义 | 尺寸探测返回 null 是正常行为，不能把它当作流打开失败；预算必须在实际解码前判断 |
| [Android 文件访问说明](https://developer.android.com/training/data-storage/shared/documents-files) | 使用系统文件创建流程取得目标 URI | API 26–28 通过用户选择的位置保存，不使用缺少权限处理的旧版外部 MediaStore 写入 |

本轮根据上述思路按项目架构实现，没有直接复制整段第三方源码或引入 Compressor 依赖。此前文档提及的 Squoosh、DevToys、Luban 等名称不作为已完成源码研究的依据；本轮 Luban 的尝试链接无法取得源码，未据此宣称采用其实现。

开发与后续修复遵循 [工具开发约定](TOOL_DEVELOPMENT_GUIDE.md)。

## 分层与结果模型

```text
ImageCompressRoute / ImageCompressScreen
  → ImageCompressViewModel
  → ReadImageMetadataUseCase / CompressImageUseCase / SaveImageUseCase
    / PrepareImageShareUseCase / DiscardImageUseCase
  → ImageCompressRepository / LocalImageCompressRepository
  → BitmapFactory / Matrix / Bitmap.compress / 私有缓存文件
  → MediaStore 或系统创建的文档 URI / FileProvider 分享副本
```

- UI 只负责系统选图、创建文件与分享面板，不读取图片流、不写文件。
- `CompressResult` 保存输出 ID、真实编码后的预览及元数据，不保存整份输出 ByteArray。输出 ID 对应工具自己生成的文件名，Repository 校验它不能包含路径。
- 来源文件名读取后限制长度，导出名称先截取最多 80 字符再去扩展名/过滤路径字符，不在过滤完巨量文本后才检查长度。
- 图片解码、编码、导出和清理在 IO 线程执行。Repository 使用互斥锁串行执行原生工作；应用级清理任务负责释放页面不再使用的私有结果。

## 功能与输出规则

| 功能 | 当前行为 |
| --- | --- |
| 选图 | 系统图片选择器；解码支持 JPEG、PNG、WebP 静态像素 |
| 尺寸模式 | 原尺寸、10%–100% 等比缩放、最长边限制；默认最长边 1920 px，不放大源图 |
| 质量 | 1–100；50/70/80/90/100 快捷选项；PNG 不使用此参数，界面禁用质量控制并说明 |
| 格式 | 跟随源格式、JPEG、PNG、WebP；API 30+ 的 WebP 质量 100 使用显式无损模式，其余质量使用有损模式 |
| 透明背景 | PNG/WebP 保留支持的透明像素；JPEG 将透明区域合成白色，界面明确提示 |
| 方向 | 解析 EXIF 并处理正常、旋转、水平/垂直镜像和转置等八种标准方向；解析失败时采用默认方向 |
| 预览 | 原图与压缩后两个页签；压缩后预览总是从编码文件采样解码，包含小图；尚无结果时不把原图当作压缩结果 |
| 统计 | 输出文件实际大小、前后尺寸、压缩率、节省大小；来源不提供大小时显示未知，不伪造 0% 或用 `available()` 当作文件大小 |
| 保存 | API 29+ 保存到 `Pictures/CangshuoToolbox`；API 26–28 显示“选择位置保存”，通过系统文件创建器写入 |
| 分享 | 后台复制独立分享文件后，通过 FileProvider、ClipData 和临时只读授权打开系统分享面板 |

PNG 的无损仅指编码方式，缩放仍改变像素。API 26–29 使用平台旧版 WebP 编码器，不承诺与 API 30+ 无损选项完全一致。重新编码不复制原始 EXIF/GPS 等元数据。

当前不支持批处理、动画帧保留、裁剪、用户指定任意角度旋转或完整原始元数据保留。GIF 等不支持的解码格式返回错误；动画 PNG/WebP 不提供动画保真承诺，输出是静态图片。

## 资源限制

限制集中在 `ImageProcessingLimits`，均在完整解码或输出分配前尽量校验：

- 源图尺寸必须有效，最长边不超过 100,000 px、像素不超过 500,000,000；此上限不表示能原尺寸处理所有这些图片。
- 输出最长边不超过 8192 px，像素不超过 8,000,000；原尺寸与百分比模式也遵守这些限制。超限提示用户选择更小的最长边，不静默更改用户要求的原尺寸。
- 根据采样后的像素、目标像素、旋转/缩放/透明合成的重叠 Bitmap 及 16 MiB 预览/编码预留估算工作内存。
- 本工具的估计预算为 `min(96 MiB, 应用最大堆的 1/3)`；低内存设备实际允许的尺寸可能更小。
- 原图/结果预览按最长边不超过 1024 px 选择采样值，处理狭长图时也会采样；预览预算按实际采样宽高估算，低堆设备可进一步增加采样值。
- 单次编码文件不超过 32 MiB；编码到受限输出流，超过限制中止并清理不完整结果。保存/分享使用 32 KiB 缓冲区流式复制。
- 私有结果与分享目录的合计预算为 256 MiB；旧缓存满 24 小时后按需清理，当前使用的私有输出排除在过期清理之外。到达预算时返回空间提示，不删除尚在保留期内的分享文件来腾空间。

估算与守卫用于降低内存风险，不能保证在所有厂商解码器、其他页面占用或存储提供者行为下永不失败。原生分配失败有本地化反馈；不会因此宣称所有大图已通过设备验收。

## 任务、取消与失败

- 元数据任务与压缩任务分别跟踪。重新选图/清空时取消它们并递增请求版本；旧请求的结果不能写入新选择。
- 元数据加载完成时使用最新参数，加载期间调参不会并发启动一个没有元数据的压缩任务。
- 调参采用 150 ms 输入合并，避免每个滑块事件都编码；首次处理不增加此延迟。
- 每次调参使旧结果失效，避免当前参数和可保存结果不一致；旧私有文件由 Repository 清理。
- Bitmap 原生操作不保证立即响应取消。互斥锁限制重叠处理，阶段间检查协程状态；取消异常继续抛出，不显示为普通压缩失败。
- 保存和分享捕获当前完整结果，执行期间禁用选图/清空/设置修改，避免导出期间释放该文件。系统文件选择器取消后退出保存状态。
- 保存使用 MediaStore `IS_PENDING`；写入失败或取消时尝试删除本次创建的条目。文档写入失败时也尝试删除本次新建的文档，部分提供者可能不支持删除。
- 编码/分享未成功交付的缓存文件在 finally 中清理；分享用独立副本，清空工具不会立即删掉其他应用正在读取的分享文件。
- 错误由 ViewModel 映射为中文/英语资源，不把原始异常、路径或堆栈展示给用户。读取/压缩失败提供重试；保存/分享失败解除相应忙碌状态。

## UI 与语言

沿用现有 Stitch 参考及共享主题，预览区域使用 `ToolboxLoadingState`，统计和导出操作使用 `ToolboxLoadingIndicator`。失败且无预览时显示占位文字，避免无限进度动画。

统计标题、处理提示、节省大小、分享面板标题、重试、关闭和新错误均维护中文/英语资源；默认语言行为继续遵循应用设置。成功/失败颜色采用主题 role。`100%` 离线文案显式关闭格式化，修复 Lint 字符串格式警告。

## 服务端目录

已有 `V13__seed_image_compress_tool.sql` 保持原样：编码 `image_compress`、分类 `IMAGE`、模式 `LOCAL`、排序 220。此轮仅调整本地执行契约，没有外部 API、数据库、依赖或权限变更。

## 2026-10-04 修复记录与验证

本轮修复大图预算、正常尺寸探测误判、旧选图覆盖、输出内存副本、压缩预览失真、旧系统保存、主线程分享文件写入、取消处理、失败加载状态及图片页加载/语言不一致等问题。

使用本机 Android Studio JDK 与已有 Gradle 缓存，离线执行 `:app:assembleDebug :app:lintDebug`。本轮三次构建均成功，最终收尾构建耗时 1 分 5 秒；最终 Lint 为 0 错误、27 条警告（本轮开始为 32 条），`git diff --check` 通过。

Debug APK：`android/app/build/outputs/apk/debug/app-debug.apk`；Lint 报告：`android/app/build/reports/lint-results-debug.html`，均在 D 盘项目目录。本轮创建的 `.tmp/image-compress-fix/` 在构建结束后清理。

未新增或运行自动化测试，未启动真机或模拟器；构建通过不表示下面的设备清单已经通过。服务端未修改，未重跑 Server 或数据库联调。工具改动仍未提交。

## 后续设备验收

1. JPEG/PNG/WebP 正常选图与默认 1920 px 输出；中文/英语、浅色/深色、大字号。
2. 原尺寸超限、百分比超限、低堆预算、10000×10000 大图、超宽/超高长图、无效和损坏文件。
3. 小图低质量 JPEG 的真实压缩预览；透明图导出白底 JPEG；EXIF 八方向。
4. 快速 A→B 选图、加载期间清空、连续调参、返回离开，确认无旧任务覆盖和错误卡住的进度。
5. 图片选择器不提供 SIZE、编码失败、缓存满、文档/相册写入失败与取消。
6. API 26/28 的文件选择保存及取消、API 29+ 的相册保存、分享读取正确输出；无可用接收应用时显示错误。
7. 导出期间回到/离开页面、文件选择器期间旋转、进程被系统回收后的恢复。当前没有持久化处理中图片、参数或待导出快照，进程重建不承诺恢复该任务。

以上为待执行清单，不能作为测试通过记录。
