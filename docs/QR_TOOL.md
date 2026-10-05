# 二维码工具

工具编码 `qr`，分类 `QR`，模式 `LOCAL`，排序 210，默认启用并推荐。通过现有 `ToolRegistry` 接入首页、目录、搜索和工具容器。当前实现文本/网址与 Wi-Fi 二维码生成、相机实时扫描、系统选图识别，以及识别结果的复制、分享、重新生成和 PNG 导出。

本轮（2026-10-04）修复主线程读图/识别、无采样解码、Wi-Fi 字段转义、原始异常及配色名称未本地化等问题。随后完成多语言收尾：将 WPA/WPA2、WEP 与 L/M/Q/H 容错等级标签从代码字面量移入中英资源（协议标记保留标准原文、两端同值），QR feature 不再包含用户可见硬编码文案。再补齐 PNG 保存/分享、识别原文分享和重新生成；随后由 CameraX 接入相机实时扫描，并把解码范围从二维码扩展到 13 种常见二维码/条码；接着补齐电话、邮箱、短信、联系人和日历事件 5 种结构化内容类型；最后为相册解析加入多二维码列表模式。同一图片的多个条码/混合多码、相机连续收集、vCard/多号码、事件时区与其他未列能力仍未实现，路线图条目保持未勾选。

相机扫描同样支持 13 种符号体系，因此工具卡片的“二维码”描述保持不变，避免仅为一句话新增服务端迁移；搜索与识别入口通过 `条码`、`barcode` 关键词和解析页说明体现该能力（见第 7 节）。

## 1. 实际开源参考

2026-10-05 新增扫码历史：默认关闭、Room v4 保存最新 100 项，查看结果/删除/确认清空，敏感配置排除和原文预算。相册与相机成功结果自动接线，103 个 Android 测试和 12 项主机 SQLite 检查通过，构建/Lint 0 错误/22 既有警告。具体来源、适配与边界见 [扫码历史](QR_HISTORY.md)。下方日期记录保留当时范围。

查阅日期：2026-10-04。以下是本轮实际阅读的业务源码，不表示过去已研究整个仓库。

| 来源与版本 | 阅读内容 | 本项目采用与适配 |
| --- | --- | --- |
| ZXing [`WifiResultParser.java`](https://github.com/zxing/zxing/blob/zxing-3.5.3/core/src/main/java/com/google/zxing/client/result/WifiResultParser.java)，`zxing-3.5.3` | SSID、认证类型、密码和隐藏网络字段的解析语义 | 使用对应 Wi-Fi 内容格式；开放网络省略密码，隐藏网络写布尔字段；不实现企业网络配置或自动连接 |
| ZXing [`ResultParser.java`](https://github.com/zxing/zxing/blob/zxing-3.5.3/core/src/main/java/com/google/zxing/client/result/ResultParser.java)，`zxing-3.5.3`；[Wi-Fi 内容约定](https://github.com/zxing/zxing/wiki/Barcode-Contents#wi-fi-network-config-android-ios-11)，Wiki 可变化 | 分隔符识别、反斜杠反转义以及五种特殊字符规则 | 在领域层逐字符转义反斜杠、分号、逗号、双引号和冒号；不做 URL 编码，不修剪或改写字段 |
| ZXing [`MECARDContactEncoder.java`](https://github.com/zxing/zxing/blob/zxing-3.5.3/android/src/com/google/zxing/client/android/encode/MECARDContactEncoder.java)、[`ContactEncoder.java`](https://github.com/zxing/zxing/blob/zxing-3.5.3/android/src/com/google/zxing/client/android/encode/ContactEncoder.java)、[`VCardContactEncoder.java`](https://github.com/zxing/zxing/blob/zxing-3.5.3/android/src/com/google/zxing/client/android/encode/VCardContactEncoder.java)，`zxing-3.5.3` | MECARD 的字段顺序、保留字符转义规则、末尾分号，以及 vCard 3.0 备选方案 | 采用 MECARD（更紧凑、不依赖 Android ContactsContract），按同一规则转义反斜杠、冒号、分号并移除换行；不生成 vCard、多号码、地址、备注、网址或电话类型元数据 |
| ZXing [`QRCodeMultiReader.java`](https://github.com/zxing/zxing/blob/zxing-3.5.3/core/src/main/java/com/google/zxing/multi/qrcode/QRCodeMultiReader.java)、[`MultipleBarcodeReader.java`](https://github.com/zxing/zxing/blob/zxing-3.5.3/core/src/main/java/com/google/zxing/multi/MultipleBarcodeReader.java)，`zxing-3.5.3` | 单图多码检测的 `decodeMultiple` API、结果集合与未找到时的 `NotFoundException` | 多码模式调用该 Reader，按检测顺序输出并去掉重复载荷；未找到时回退单码路径，因此图片里只有条码时仍可识别；不实现多格式混合多码 |
| ZXing [`ByQuadrantReader.java`](https://github.com/zxing/zxing/blob/zxing-3.5.3/core/src/main/java/com/google/zxing/multi/ByQuadrantReader.java)、[`GenericMultipleBarcodeReader.java`](https://github.com/zxing/zxing/blob/zxing-3.5.3/core/src/main/java/com/google/zxing/multi/GenericMultipleBarcodeReader.java)，`zxing-3.5.3` | 通过裁剪子图重复解码来寻找多个符号；通用实现按象限递归 | 借鉴裁剪重试思路，但改为固定三条重叠横向分带（`QrBandDecoder`），把耗时限制在一次全图量级；通用递归实现在相机尺寸下可能产生数百次解码，因此不采用 |
| ZXing [`QRCodeWriter.java`](https://github.com/zxing/zxing/blob/zxing-3.5.3/core/src/main/java/com/google/zxing/qrcode/QRCodeWriter.java)，`zxing-3.5.3` | 默认四模块留白、矩阵输出与容错选项 | 保持四模块留白；生成最小矩阵后由 Compose 绘制，后台编码；容量不足使用本地化提示 |
| ZXing [`QRCodeReader.java`](https://github.com/zxing/zxing/blob/zxing-3.5.3/core/src/main/java/com/google/zxing/qrcode/QRCodeReader.java)，`zxing-3.5.3` | 检测器、解码结果和镜像修正路径 | 2026-10-04 起相机与相册改用 `MultiFormatReader` 与 13 种格式清单，QR 专用 Reader 不再单独调用；不声明多码、结构化追加内容合并或所有旋转/镜像图片均可识别 |
| JourneyApps [`Decoder.java`](https://github.com/journeyapps/zxing-android-embedded/blob/master/zxing-android-embedded/src/com/journeyapps/barcodescanner/Decoder.java)，`master` 可变化 | 专用线程识别、二值化及 Reader 重置 | 借鉴后台识别和生命周期管理；本项目针对静态图片加入预算、串行互斥、取消检查和有限重试，不引入其相机依赖 |
| [Android 大图加载说明](https://developer.android.com/topic/performance/graphics/load-bitmap) | 先探测尺寸，再按采样值解码 | 结合二维码需要的清晰度与本项目内存上限计算采样；全景图也按最长边限制，不完整解码原图 |
| [CameraX ImageAnalysis](https://developer.android.com/media/camera/camerax/analyze) 官方文档 | 分析用例的背压策略、分辨率选择与执行线程 | 使用 `STRATEGY_KEEP_ONLY_LATEST`、4:3 分辨率选择器和 1280×720 目标；分析在独立单线程执行器，不阻塞主线程 |
| JourneyApps [`SourceData.java`](https://github.com/journeyapps/zxing-android-embedded/blob/master/zxing-android-embedded/src/com/journeyapps/barcodescanner/SourceData.java)，`master` 可变化 | 相机原始帧按显示方向旋转后再建立 `PlanarYUVLuminanceSource` | 采用“先摆正、再交给 ZXing”的顺序；本项目自行按 `rotationDegrees` 处理 90°/270° 而不是旋转 Bitmap，也不做 JPEG 中转 |
| JourneyApps [`Decoder.java`](https://github.com/journeyapps/zxing-android-embedded/blob/master/zxing-android-embedded/src/com/journeyapps/barcodescanner/Decoder.java)、[`DefaultDecoderFactory.java`](https://github.com/journeyapps/zxing-android-embedded/blob/master/zxing-android-embedded/src/com/journeyapps/barcodescanner/DefaultDecoderFactory.java)，`master` 可变化 | 单帧解码不应抛出、`MultiFormatReader.decodeWithState` 优化、解码后 `reset()`、`POSSIBLE_FORMATS`/`CHARACTER_SET` 提示 | 每帧解码失败返回空并继续下一帧；`reset()` 放在 `finally`；提示集合与字符集按同一思路设置，另加 `TRY_HARDER` 以适应预览帧与照片 |
| ZXing [`PlanarYUVLuminanceSource.java`](https://github.com/zxing/zxing/blob/zxing-3.5.3/core/src/main/java/com/google/zxing/PlanarYUVLuminanceSource.java)，`zxing-3.5.3` | 行索引按 `(y + top) * dataWidth + left` 计算，构造数据必须紧密排列 | 拷贝 Y 平面时去掉 `rowStride` 填充，再交给 ZXing；相机帧只读取亮度平面，不做 RGB 转换 |
| ZXing [`QRCodeEncoder.java`](https://github.com/zxing/zxing/blob/zxing-3.5.3/android/src/com/google/zxing/client/android/encode/QRCodeEncoder.java)，`zxing-3.5.3`，本次通过 raw 源码读取成功 | `encodeAsBitmap` 的矩阵到 Bitmap 像素映射 | 输出使用当前结果矩阵，整数倍放大、居中和保留四模块留白；采用按模块绘制，避免另分配整幅 IntArray；固定配色、尺寸、内存预算与后台执行 |
| ZXing [`EncodeActivity.java`](https://github.com/zxing/zxing/blob/zxing-3.5.3/android/src/com/google/zxing/client/android/encode/EncodeActivity.java)，`zxing-3.5.3` | PNG 编码及 `ACTION_SEND` 图片分享流程 | 借鉴“先生成 PNG 再分享”工作流；本项目用随机文件名、FileProvider content URI/临时读授权，替换该旧版外部目录/file URI 和内容派生文件名；图片分享不附带额外原文 |
| [Android MediaStore](https://developer.android.com/training/data-storage/shared/media)、[文件分享](https://developer.android.com/training/secure-file-sharing/share-file)、[SAF 创建文件](https://developer.android.com/training/data-storage/shared/documents-files#create-a-new-file) | Pending 发布、失败清理、URI 授权和系统创建文件 | API 29+ 写自有媒体，API 26–28 用 CreateDocument；复用项目现有图片压缩导出结构，新增二维码专用缓存路径，不增加存储权限 |

本轮按上述规则独立实现 Kotlin 分层逻辑，调用已存在的 ZXing Core 3.5.3；没有复制第三方源码、升级依赖或新增相机/存储权限。导出任务中网页工具读取 GitHub raw 失败，随后通过 PowerShell HTTPS 成功读取上述两份 ZXing Android 源码；IT-Tools 的候选 qrcode-generator 路径返回 404，未记为已参考实现。

## 2. 分层与文件范围

```text
QrRoute / QrScreen
  → QrViewModel（输入、任务版本、加载/错误/重试状态）
  → GenerateQrUseCase / DecodeQrUseCase
  → QrInputPolicy / QrRepository
  → LocalQrRepository / QrImagePolicy
  → ContentResolver + BitmapFactory + ZXing

QrRoute / QrScreen
  → QrViewModel（导出快照、状态、一次性分享请求）
  → SaveQrImageUseCase / PrepareQrShareUseCase
  → QrExportRepository / QrExportLayout
  → LocalQrExportRepository
  → Bitmap + PNG + MediaStore / SAF / FileProvider
```

界面只提交选中的 URI，不打开文件流、解码 Bitmap 或调用 ZXing。Repository 使用应用 Context，生成在 `Dispatchers.Default` 执行；读图在 `Dispatchers.IO`，像素转换与识别在 `Dispatchers.Default`。

新增策略类位于二维码 feature；容器仅调整 Repository 的 Context 注入。中文和英语资源、工具文档、路线图及交接/审查记录同步更新。Server 接口、数据库结构、V12 迁移及其他工具业务代码未改变。

## 3. 已实现功能

| 流程 | 当前行为 |
| --- | --- |
| 文本/网址生成 | 输入、粘贴、示例、清空；保留大小写、换行和首尾空格；使用 UTF-8，不访问网址 |
| Wi-Fi 生成 | SSID、密码、WPA/WPA2、WPA3、WPA2/WPA3 企业 EAP、WEP、开放网络、隐藏网络；特殊字符转义；身份/匿名身份/阶段二按协议字段输出；密码显示/隐藏切换 |
| 电话 / 邮箱 / 短信 | `tel:`、`mailto:`（主题/正文/抄送/密送，RFC 6068 百分号编码）、`SMSTO:` 载荷；号码与邮箱格式校验；短信正文可留空 |
| 联系人 | MECARD（可多个 `TEL`/`EMAIL`）或 vCard 3.0（多电话/邮箱/地址，RFC 6350 转义）；按 ZXing 规则转义并移除换行；地址仅 vCard 输出 |
| 日历事件 | VEVENT 的 `SUMMARY`/`LOCATION`/`DESCRIPTION`/`DTSTART`/`DTEND`；时间基准可选本地浮动或按设备时区转 UTC；可选 DISPLAY 提醒（准时/5/15/30 分钟、1 小时、1 天）；严格日期时间校验；结束可留空 |
| 手动框选识别 | 解析页可打开框选面板：点按移动方形选区、滑块调整大小，只解码选区；预览限制 1024 px，选区几何与解码共用 QrCropPolicy，选区始终位于图片内 |
| 扫码历史 | 默认关闭；开启后保存最新 100 条识别结果，支持查看、删除与确认清空；Wi-Fi/OTP 与超限内容不保存，详见 [扫码历史](QR_HISTORY.md) |
| 线性条码生成 | CODE 128、CODE 39、CODE 93、EAN-13、EAN-8、UPC-A、ITF、Codabar 与 QR Code 共用符号类型选择；只编码纯文本，逐格式校验字符集、长度与 GS1 校验位，不改写输入；预览与 PNG 导出按整数条宽绘制 |
| 生成选项 | L/M/Q/H 四种容错等级、四套固定深色模块/白色背景配色；中文/英语名称 |
| 生成结果 | 四模块留白、整数像素网格绘制、完整原文选择与复制；不复制省略号或旧输入的结果 |
| 图片识别 | 系统图片选择器、后台探测与采样、透明像素合成到白底、正常亮度与反色各一次识别 |
| 识别结果 | 展示完整解码文本与符号名称（QR Code、EAN-13 等）并复制；失败可重试或重新选图；加载时可取消或换图 |
| 导出 | PNG/JPEG/SVG；512/1024/2048 像素，默认 1024；当前矩阵和配色快照；整数网格与原留白；PNG/JPEG 走相册（API 29+）或系统文件位置，SVG 始终使用系统文件选择器 |
| 图片分享 | 后台生成独立 PNG，通过系统分享页授予 URI 读取；不自动选择接收方或发送，不附加内容原文 |
| 识别原文操作 | 通过系统分享页分享完整原文；重新生成转入文本生成页，保留空格/换行/大小写，超出 2000 字符或不完整 Unicode 时拒绝而不截断 |
| 相机扫描 | 按需申请相机权限；CameraX 预览 + 实时识别；识别成功自动停止并填入结果；无相机/权限被拒/启动失败分别提示与重试；有闪光灯时提供手电筒 |
| 符号体系 | 相机与相册共用 13 种格式：QR Code、Data Matrix、Aztec、PDF417、CODE 128/93/39、Codabar、ITF、EAN-13/8、UPC-A/E |
| 多码识别 | 相册解析或相机扫描可开启“识别多个二维码”：相册列出同图全部二维码，相机持续收集并显示数量；均支持逐条复制、整体复制或分享，只有 1 个结果时仍走原结果卡 |
| 状态 | 空、真实加载、成功和有原因的错误；共用 `ToolboxLoadingState`，没有最低展示时长 |

容错百分比为等级的常见近似说明，不保证任意面积的遮挡、损坏或低质量照片可识别。内容越长、容错越高，矩阵通常越密，实际扫描效果需要设备验收。

## 4. 输入与 Wi-Fi 规则

- 文本编辑上限为 2000 个 Kotlin 字符单元（UTF-16 单元，emoji 可能占两个）。Wi-Fi 单个编辑字段上限为 256；超长输入被拒绝并明确提示，保留上一份已接受输入，不静默截断。
- UseCase 再次检查输入，拒绝不完整 Unicode。Repository 在编码前限制内容为 6000 UTF-8 字节；这是计算资源限制，不是容量承诺。ZXing 仍按实际字符、编码模式和容错级别决定是否能装入二维码。
- SSID 产品上限为 32 UTF-8 字节。SSID 和被使用的密码不允许 C0 控制字符及 DEL；文本二维码仍可包含换行。保留字段的首尾空格，不做 Unicode 归一化。
- Wi-Fi 安全类型只接受固定三项；加密网络要求非空密码，开放网络忽略内存中原有密码并省略 `P` 字段。切换回加密模式可继续编辑原密码；清空会清除它。
- 特殊字符逐个加反斜杠，包含反斜杠自身；例如名称 `a;b` 输出字段为 `S:a\;b;`。隐藏网络写 `H:true` 或 `H:false`。
- 本工具生成连接配置文本，不连接、探测或验证路由器。密码长度/字符是否符合具体 WPA/WEP 网络要求、扫描器对纯十六进制外观名称/密码的解释、Unicode 凭据和实际连接兼容性仍由目标网络/扫描器决定；不自动给字段加额外引号。
- 密码默认遮盖，只保存在 ViewModel 内存；从旧版状态恢复时移除 `wifi_pass`。旋转页面保留 ViewModel 时密码仍在，进程重建需要重填。生成结果和主动复制内容会包含配置密码。

### 结构化内容载荷

- **电话**：载荷为 `tel:<输入>`。只允许数字、`+`、空格、`-`、括号和点，且至少 2 位数字；保留用户输入的原始分隔符，不做重写或去掉括号。
- **邮箱**：载荷为 `mailto:<地址>`，可选 `?subject=` 与 `&body=` 头部字段。地址要求恰好一个 `@`、本地部分非空、域名包含点且没有首尾点或连续点；主题与正文按 RFC 6068 用百分号编码（只保留 `A-Za-z0-9-._~`），正文支持换行并编码为 `%0A`。不写入抄送、密送或附件。
- **短信**：载荷为 `SMSTO:<号码>:<正文>`。号码规则同电话；正文可留空但仍保留第二个冒号，正文上限 500 字符。
- **联系人**：MECARD 格式，字段顺序与 ZXing `MECARDContactEncoder` 一致（`N`、`ORG`、`TEL`、`EMAIL`），按该实现转义反斜杠、冒号、分号，移除换行并在末尾追加一个分号；姓名必填，电话或邮箱填写时按各自规则校验。不生成 vCard、地址、备注、网址或多号码。
- **日历事件**：VEVENT，字段为 `SUMMARY`（必填）、`LOCATION`、`DESCRIPTION`、`DTSTART`、`DTEND`。日期按 `uuuu-MM-dd`、时间按 `HH:mm` 使用 STRICT 解析，`2026-02-30`、`25:00` 等被拒绝；年份范围 1900–3000。结束时间必须同时给出日期和时间，且必须晚于开始；不填写结束则省略 `DTEND`。文本按 iCalendar 规则转义反斜杠、分号、逗号与换行；纪要允许多行，其余字段拒绝控制字符。
- **时间语义**：事件使用本地浮动时间 `YYYYMMDDTHHmmss`（不带 `Z`、不带 `TZID`），由扫描端按自己的本地时区解释。本工具不读取设备时区、不做夏令时换算。
- **输入上限**：结构化字段单个最多 256 字符；短信正文、邮件正文与事件纪要各 500，邮件主题 256，日期 10，时间 5；超长输入被拒绝并提示，不静默截断。所有字段要求合法 UTF-8。
- **示例数据**：示例按钮填入的是载荷数据（如 `13800138000`、`hello@example.com`），保持语言中性，不作为界面文案翻译对象。

## 5. 图片资源、权限与失败边界

系统选图不新增广泛存储或相机权限。当前仅接受选择器授予访问的 `content://` URI；页面取消选图不产生识别错误。选择器可能提供云端媒体，获取原图是否需网络由系统/provider 决定；本工具没有业务上传或识别请求。

| 限制 | 当前值与时机 |
| --- | --- |
| 原始尺寸 | 先只读取边界；拒绝单边超过 100,000 或总像素超过 500,000,000 的图片；用 Long 计算 |
| 识别采样 | 二次幂采样；预计识别最长边不超过 2048，像素不超过 4,000,000；低内存设备进一步采样 |
| 工作预算 | 估算 `采样像素 × 12 字节 + 16 MiB`；不得超过 `min(96 MiB, 最大堆/3)`；覆盖 Bitmap、ARGB、灰度和识别工作空间 |
| 分配前复核 | Bitmap 解码后按实际宽高再检查，随后才分配 `IntArray(width × height)` |
| 流读取预算 | 每次打开流最多读取/跳过 64 MiB，另读一个字节用于识别超限；边界探测与实际解码分别打开流；这是读取预算，非源文件总大小承诺 |
| 识别尝试 | QR 专用 Reader，正常亮度和反色最多各一次；使用 `TRY_HARDER`；不无限重试 |
| 释放 | 流使用 `use` 关闭，Bitmap 在 `finally` 回收，Reader 在每次尝试后重置；不生成临时图片或持久识别缓存 |

尺寸探测的 `inJustDecodeBounds` 正常返回 null，不能把它视为图片打开失败。打开流失败和实际像素解码返回 null 分别处理。

预算降低峰值分配风险，不保证所有厂商解码器和其他进程/页面内存压力下绝无 OOM；OOM 映射为内存反馈。采样可能丢失大照片里的小二维码；用户可先裁剪到二维码附近。格式支持由 Android BitmapFactory 决定，动画不遍历所有帧，不解码任意文件或二进制附件。

错误区分：无效图片、读取/访问失败、资源超限、未找到二维码、数据格式/校验失败和通用识别失败。页面使用资源 ID 映射中文/英语，避免展示原始异常。

### 相机扫描与符号体系

- **权限按需申请**：只有点击“打开相机扫描”才请求 `CAMERA`；工具打开、生成、相册解析都不需要相机权限。`QrToolDefinition.requiredPermissions` 因此保持为空——该字段由 `OpenHomeToolUseCase` 在打开工具前检查，用它拦截会让整个二维码工具在未授权时不可用，与“可选能力”的设计冲突。系统设置中勾选后返回页面即可重试。
- **设备与清单**：清单声明 `CAMERA` 权限和 `android.hardware.camera.any`（`required=false`），无相机设备仍可安装并使用相册解析；无相机时页面直接说明并停止，不发起权限请求。
- **符号体系**：相机与相册共用同一个 `MultiFormatReader` 和格式清单，见 `QrBarcodeFormats`。RSS、MAXICODE 等部分支持或小众读码器未启用；CODE 39、ITF、Codabar 没有强制校验位，纹理复杂的照片可能出现误识别，结果需要用户自行确认。
- **帧处理**：只读取 Y 亮度平面，按 `rowStride` 去掉行填充后交给 `PlanarYUVLuminanceSource`；超过 4,000,000 像素的帧直接丢弃。按 `ImageInfo.rotationDegrees` 把 90°/270° 帧转正，0°/180° 复用同一缓冲区（ZXing 的 1D 读码器双向扫描、2D 符号对半转不变），因此每帧最多一次整帧拷贝加一次旋转写入。
- **背压与线程**：`ImageAnalysis` 使用 `STRATEGY_KEEP_ONLY_LATEST` 与 4:3 分辨率选择器（目标 1280×720），分析运行在独立单线程执行器，识别不占用主线程；慢帧被新帧替换而不是排队。
- **识别成功**：第一帧解出结果后立即停止接收后续帧、关闭相机并填入解析结果，避免重复触发；识别失败（未找到）不算错误，继续下一帧。相机内容不会自动打开链接、连接 Wi-Fi 或执行其他动作。
- **生命周期**：预览绑定 `LocalLifecycleOwner`，应用进入后台时由 CameraX 释放相机；关闭相机、切换模式、选择相册图片、分享/重新生成或离开页面都会解绑并关闭分析线程。滚动导致预览离开组合时同样解绑，回到页面后重新绑定。
- **手电筒**：仅在 `hasFlashUnit` 为真时显示；开启请求失败会恢复开关状态并提示，不保留错误状态。
- **失败分类**：无可用相机、权限被拒、相机启动失败（例如被其他应用占用）三种情况分别有说明文案；启动失败提供重试，且重试会重建预览视图而不是复用已释放的 Surface。
- **仍未覆盖**：单码模式下只取第一次结果；多码模式的相机收集见下一节；没有扫描历史、区域裁剪、自动变焦或曝光调节；相机被其他应用长期占用时无法预览。

### 多码识别（相册与相机）

- **开关**：解析页提供“识别多个二维码”，默认关闭并随 SavedState 恢复；切换开关会丢弃上一份解析结果，避免列表与单码结果混用。
- **行为**：关闭时走单码路径（13 种格式）。开启时先用 ZXing `QRCodeMultiReader` 做多码检测：找到 1 个仍按单码展示，找到 2 个及以上展示列表（按检测顺序、重复载荷只保留一次）；一个都没找到时回退单码路径，所以图片里只有一个线性条码时依旧能识别。
- **线性条码分带**：QR 多码未命中时，QrBandDecoder 把采样后的帧按固定三条重叠横向分带各解码一次（单条带约占 1/3 高度、12% 重叠），按带宽顺序去重输出；成本接近一次全图解码，跨带边界的条码至少落在一条完整带内。找到 1 个/多个分别进入原结果卡或结果列表，都没找到才回退单码路径做错误分类。该路径只处理相册静帧，相机仍靠移动取景逐个收集。
- **竖向与混合**：分带同时覆盖竖向符号——ZXing 的 1D 读码器按行扫描，因此竖向条带先转置再解码；同一张图里横向与竖向条码可以同时返回。相册多码的顺序是 QR 多码读码器在前，未找到两个以上结果时再跑分带并把两侧结果按文本去重合并，因此一个二维码加若干条码的图片可以一次列全。
- **范围**：多码仍不覆盖同图两个以上二维码加额外条码的组合（QR 侧已给出多码列表时跳过分带以控制耗时），相机一次只报一个符号；通用递归多码检测（GenericMultipleBarcodeReader）按设计不采用。
- **资源与并发**：多码检测复用单码的采样结果（最长边 ≤2048、像素 ≤4M）与读取预算，同样受 `workMutex` 串行互斥、任务版本校验和取消检查约束。多码比单码耗时更长，取消按阶段协作，原生解码不能保证立即中断。
- **结果保留**：列表只存在于内存，不写入 SavedState；切换开关、清空或重新选图都会丢弃列表。逐条复制使用各条目原文，整体复制与分享用换行连接全部载荷。
- **相机收集**：多码开关打开时，相机不再在第一个结果后停止，而是持续识别并把新载荷加入本次会话（重复内容忽略，单次上限 50 个）；预览下方显示已收集数量与“完成”按钮。点击后关闭相机，1 个结果放入原结果卡，2 个及以上放入结果列表；一个都没收集到时只关闭预览并保留上一份结果。
- **相机收集状态**：收集列表只保存在 ViewModel 内存；扫描期间多码开关禁用，切换开关会同时结束相机会话并清空收集（`setMultiDecode` 是防御性兜底）；离开工具或切换模式会丢弃会话。

### 符号名称

- 单码结果卡和多码列表的每一项都会显示 ZXing 报告的符号名称：QR Code、Data Matrix、Aztec、PDF417、Code 128/93/39、Codabar、ITF、EAN-13/8、UPC-A/E。
- 名称来自 `QrBarcodeFormats.symbologyOf`；`barcodeFormat` 为空或不属于上述 13 种时显示“未知格式”。相机与相册两条路径共用同一映射，`QrDecodeEntry` 把载荷与符号一起传递到界面。
- 协议名称保留标准原文（中英同值），只有“未知格式/Unknown”是翻译文案；这些名称是格式标识，不是对识别质量的承诺。

## 6. 并发、取消与恢复

- 生成输入变化立即清除旧矩阵和旧可复制内容，取消旧 Job、递增任务版本；编辑时 150ms 防抖减少重复计算。这是输入防抖，不是延长加载动画。颜色切换仅重绘，不重新编码。
- 选新图片会取消旧任务并清除旧解码文本；结果必须同时匹配版本和当前 URI 才能写入状态。清空、取消或切换模式都会使旧识别失效，避免完成较晚的旧图重新填充页面。
- Repository 互斥锁串行化该工具的生成与识别，避免取消后原生任务尚未结束时继续并行分配大图。
- 流读/跳过、像素转换分段、各处理阶段和两次识别之间检查取消；`CancellationException` 继续向上传递。同步原生解码、正在阻塞的 provider 调用及单次 ZXing 检测不能保证立即中断，也没有硬性完成时间承诺。
- 超限标志在流返回后再次检查，避免底层解码器吞掉流异常导致错误分类丢失。即使任务被取消，已分配的 Bitmap 仍在 `finally` 回收。
- 图片 URI、解码结果和生成矩阵不写入持久缓存或 SavedState；进程重建后需重新选图/生成。普通生成输入和选项使用现有 SavedState；页面离开后的实际保留时长由工具容器生命周期决定。

### 导出、保存与分享边界

- 点击导出时深复制矩阵并固定配色和尺寸。导出/选择保存位置期间禁用输入、选项、模式切换、换图与重复导出；ViewModel 同时校验，状态结束后恢复。导出串行互斥，后台绘制和文件写入不占主线程。
- 输出严格 PNG，尺寸为 512、1024、2048；每个模块用整数像素且关闭抗锯齿，居中后的两侧额外留白差值最多 1 像素，不插值缩放或重新编码内容。支持 QR 版本 1–40 的带四模块留白矩阵（29–185 模块），拒绝形状不合法的结果。
- 分配前估算 `尺寸² × 4 + 4 MiB`，上限 `min(32 MiB, 最大堆/4)`；PNG 单文件最多 4 MiB，编码后复核超限标志，避免底层吞异常。Bitmap 在取消/失败/成功后回收，包括跨 dispatcher 返回被取消的情形。
- 导出临时文件使用 `cacheDir/qr_exports/qr_<随机UUID>.png`，名称不含原文或 Wi-Fi 密码。缓存总量预算 32 MiB，下一次导出清理超过 24 小时的文件；已分享文件保留供接收方读取，未自动定时删除。缓存被系统清理、超期或满额时需重新导出，不保证无限期访问。
- API 29+ 保存至 `Pictures/CangshuoToolbox`，先 `IS_PENDING=1`，完整写入/关闭后发布；失败或取消尝试删除未完成记录。API 26–28 CreateDocument 选择新文件位置，取消选择恢复状态，写失败尝试删除新文档；provider 不支持删除时可能遗留不完整文件。不索取广泛存储权限。
- 分享通过 `content://` FileProvider、`ClipData` 和临时读授权，不暴露整个缓存目录。图片只分享 PNG，识别原文用 `text/plain`；开启系统选择页只表示已交给系统，不宣称第三方保存或发送成功。请求在开系统页面前从 ViewModel 消费，避免重组/旋转重复打开；没有可用接收方时反馈并可重试。
- 识别结果转入文本生成页时不写 SavedState，避免把识别出的 Wi-Fi 凭据自动落入状态恢复数据；生成输入随后经用户编辑时沿用普通文本保存规则。超长内容保留在识别结果中供复制/分享，不截断后重新生成。相机/二维码内容不会自动打开链接或连接网络。
- 离开销毁 ViewModel 会取消工作；取消按阶段协作，provider 写入、原生 PNG 编码无法保证立即中断。保存成功的已发布图片不随取消删除；进程被系统强杀后导出可能需重试，未实现进程间导出恢复。

## 7. UI、多语言与待实现范围

沿用 `docs/ANDROID_UI_SPEC.md` 和 Stitch 参考：主题色阶、16dp 卡片/留白、最大内容宽度 600dp、可横向滚动的选项与键盘安全区。二维码自身的四套固定颜色属于输出数据，界面反馈仍来自共享主题。

默认跟随系统，中文回退、英语同步。预览和隐藏网络开关有本地化说明；所有本轮业务错误与配色标签使用资源。L/M/Q/H、WPA/WEP 等协议标记保留标准原文、不做语言翻译，但已全部移入中英资源（两端同值），界面代码不再直接拼接用户可见字面量。Preview 源码包含中英文，编译不代表已渲染验收。

**仍未实现：** 邮件附件（`mailto:` 协议本身不支持附件）。第三方扫码器、相册/文件提供者、日历/通讯录应用对上述载荷与导出文件的导入行为属于设备验收范围，不是客户端功能缺口。输入通用文本可容纳相应协议原文，但不等于已实现这些专用工作流。

## 8. 本轮实际验证

2026-10-05，Phase 2 功能收尾（原生导出与结构化扩展）：

- 导出格式：生成结果可选 PNG/JPEG/SVG；SVG 由纯 Kotlin QrSvgWriter 按模块网格或单行条带生成（含 1 MB 路径预算），PNG/JPEG 复用位图渲染并在 API 29+ 存入相册、API 26–28 走系统文件位置，SVG 始终使用系统文件选择器；分享意图按所选格式设置 MIME。
- 邮件：新增抄送/密送输入，按 RFC 6068 逐地址百分号编码、逗号分隔保留；非法收件人拒绝；附件不受支持已在文档说明。
- 企业 Wi-Fi：新增 WPA3、WPA2/WPA3 企业 (EAP)，支持 TTLS/PEAP/TLS 与 MSCHAPV2/GTC 阶段二、身份/匿名身份；企业网络要求身份，TTLS/PEAP 要求密码，TLS 可留空；身份与密码只保留在内存。
- 联系人：MECARD 与 vCard 3.0 可选，最多 3 条电话/邮箱/地址，MECARD 输出多个 TEL/EMAIL，vCard 额外输出 ADR；空行忽略、非法行拒绝、输入不改写。
- 新增 12 个持久化 JUnit（SVG 3、邮件/企业 Wi-Fi 5、联系人 4）。本机 141/141 通过、0 失败/错误；离线构建/Lint 成功（最终增量 47 秒），Lint 0 错误/22 条既有警告。
- 未运行真机：导出文件在相册/文件提供者/第三方应用中的实际行为、JPEG 质量、SVG 在浏览器/打印中的渲染、邮件与企业 Wi-Fi 载荷在目标应用中的导入仍待发布前设备验收。

2026-10-05，线性条码生成与密集混合多码：

- 生成侧新增 8 种线性符号，与 QR Code 共用一个符号类型选择；线性格式只编码纯文本并禁用结构化内容与容错等级，样例、SavedState 与请求校验同步。
- 字符集与长度在编码前校验，不静默改写：CODE 39/93 仅大写字母集，CODE 128 限可打印 ASCII，EAN-13/EAN-8/UPC-A 接受本体或带有效校验位的完整值，ITF 偶数位数字，Codabar 需要 A–D 起止符与合法正文字符。
- 预览与导出共用同一矩阵：线性矩阵为单行模块，界面按整数条宽拉伸绘制；PNG 导出使用 1/3 宽度高度、1/8 高度留白和整数条宽居中，分享与相册保存沿用原流程。
- 密集混合多码：QR 多码读到 2 个及以上时，额外执行一次仅线性格式的全帧扫描并去重合并，解决“多个二维码 + 条码”同图丢条码的问题；分带解码增加 1.5 秒预算参数，超时在带与带之间停止。
- 新增 15 个持久化 JUnit：条码格式/校验位/字符集/长度 7 项、导出布局 3 项、分带解码（横排/竖排/超时预算）3 项、混合帧（2 个 QR + CODE 128、纯 QR/空白）2 项。本机 129/129 通过、0 失败/错误，离线构建/Lint 成功（2 分 23 秒），Lint 0 错误/22 条既有警告。
- 未运行真机：线性条码在第三方扫码器中的实际识读率、条宽/留白在打印与屏幕下的效果、导出图片的相册/分享行为仍待发布前设备验收。

2026-10-05，扫码历史、事件时区/提醒与手动框选识别：

- 扫码历史：默认关闭、Room v4（3→4 迁移）、最新 100 条、内容+符号去重、Wi-Fi/OTP 排除、查看/复制/分享/重新生成、删除与确认清空；12 项主机 SQLite 检查通过，见 [扫码历史](QR_HISTORY.md)。
- 事件时区与提醒：DTSTART/DTEND 可选本地浮动或按设备时区转 UTC；夏令时缺口拒绝、重叠取较早偏移；可选 DISPLAY 提醒（准时/5/15/30 分钟、1 小时、1 天）。6 个新 JUnit 覆盖上海→UTC 换算、纽约 2026-03-08 缺口、2026-11-01 重叠、提醒触发与结束校验。
- 手动框选识别：解析页可打开框选面板，点按移动方形选区、滑块调整大小；预览限制 1024 px/64 MB 预算，选区几何经 QrCropPolicy 由界面与解码共用，只解码所选区域。5 个新 JUnit 覆盖整选居中、比例映射、四角约束、退化尺寸与非法输入。
- 最终离线 testDebugUnitTest/assembleDebug/lintDebug 成功（增量 1 分 27 秒），114/114 通过、0 失败/错误，Lint 0 错误/22 条既有警告；`git diff --check` 通过。
- 未运行真机/模拟器：框选触控手感、预览内存、DST 在目标设备时区的表现、旧版本 Room 迁移与第三方日历/扫码器导入仍待发布前验收。

2026-10-04，竖向与混合多码：

- QrBandDecoder 增加三条竖向分带（先转置再交给按行扫描的 1D 读码器），相册多码顺序改为 QR 多码 → 分带 → 单码回退，并把 QR 与分带结果按文本去重合并。
- JBR 直接执行已编译类：10 项检查覆盖三条横向条码、三条竖向条码、横竖混合、跨方向重复折叠、空白帧与小尺寸帧，全部通过；首次发现竖向条带未转置时无法识别，修正后复验通过。
- 离线 assembleDebug/lintDebug 成功（1 分 17 秒），0 错误、24 条既有警告、0 hint，二维码源码无 Lint 问题；APK 17,652,879 字节。
- 未运行真机/模拟器：真实照片上的竖向识别率、分带耗时与内存峰值仍待验收。

2026-10-04，线性条码分带多码检测：

- 新增 QrBandDecoder：QR 多码未命中时把采样帧按三条重叠横向分带各解码一次（12% 重叠、去重、按带宽顺序），成本接近一次全图解码；参考 ZXing ByQuadrantReader 的裁剪重试思路，不采用可能在相机尺寸下产生数百次解码的 GenericMultipleBarcodeReader。
- LocalQrRepository 在 multi 模式下的顺序改为：QR 多码 → 分带多码 → 单码回退（错误分类）；找到 1 个进原结果卡、多个进结果列表。
- JBR 直接执行已编译类：10 项检查覆盖单条码、三条带各一个条码（含 EAN-13 标注）、跨带边界条码、重复载荷折叠、空白帧、纯二维码帧与小尺寸帧，全部通过；未覆盖 Android 位图加载与真机耗时。
- 离线 assembleDebug/lintDebug 成功（1 分 30 秒），0 错误、24 条既有警告、0 hint，二维码源码无 Lint 问题。
- 未运行真机/模拟器：分带在真实照片上的识别率、竖向条码、超大图耗时仍待验收。

以下保留邮件/纪要等更早的修复记录：
2026-10-04，邮件头部字段与事件纪要：

- 领域新增 `QrFormInput.emailSubject`/`emailBody` 与 `QrEventInput.description`；`QrInputPolicy` 增加 RFC 6068 百分号编码（仅保留 `A-Za-z0-9-._~`）、主题/正文长度与 UTF-8 校验，事件校验区分“其余字段拒绝控制字符”和“纪要允许多行”。
- 邮箱在无主题/正文时仍是纯 `mailto:<地址>`；事件在纪要为空时不输出 `DESCRIPTION` 行。页面新增邮件主题、邮件正文与事件纪要三个字段（后两者多行），随 SavedState 恢复，示例按钮一并填入。
- Android Studio JBR 直接执行已编译类：20 项检查覆盖纯地址、仅主题、主题+正文的百分号编码往返（含中文、emoji、`&`/`#` 与换行）、未编码字符检查、主题 257 与正文 501 字符超限、错误地址、空纪要省略 `DESCRIPTION`、多行纪要转义、纪要超限与控制字符拒绝，以及 mailto/VEVENT 的真实二维码往返，全部通过。
- 离线 `:app:assembleDebug :app:lintDebug` 成功（1 分 51 秒），0 错误、24 条既有警告、0 hint，二维码源码无 Lint 问题；`qr_*` 中英各 158 个 key 对齐且引用齐全。
- 未运行真机/模拟器：邮件应用对 `subject`/`body` 的预填、日历应用对多行 `DESCRIPTION` 的显示、长文本在表单中的滚动仍待验收；APK 17,652,879 字节。

以下保留符号名称与更早的修复记录：

2026-10-04，识别结果符号名称：

- 新增 `QrSymbology` 枚举与 `QrDecodeEntry(text, symbology)`；`QrDecodeResult.Success`/`Multiple` 改为携带条目，`QrBarcodeFormats.symbologyOf` 统一把 ZXing 的 `barcodeFormat` 映射为 13 种格式加 `UNKNOWN`；相机分析器、单码路径与多码路径都从同一次解码结果里取格式。
- 界面在单码结果卡与多码列表每一项显示符号名称（主题色 label）；协议名称中英同值保留标准原文，只有“未知格式/Unknown”翻译；UiState 相应改为 `decodeEntry`/`decodeEntries`，相机收集列表也改为条目集合。
- Android Studio JBR 直接执行已编译类：27 项检查覆盖 13 种格式映射、`null`/MAXICODE/RSS_14 回退到 `UNKNOWN`，以及真实解码路径的格式标注（QR、Code 128、Code 39、EAN-13 单码与多码）。
- 离线 `:app:assembleDebug :app:lintDebug` 成功（1 分 21 秒），0 错误、24 条既有警告、0 hint，二维码源码无 Lint 问题；`qr_*` 中英各 152 个 key 对齐且引用齐全。
- 未运行真机/模拟器：标签在窄屏、放大字号与深色主题下的显示，以及 13 种格式的真实样本核对仍待验收；APK 17,647,987 字节。

以下保留相机多码收集与更早的修复记录：

2026-10-04，相机多码收集：

- 多码开关打开时相机进入收集模式：`QrCameraAnalyzer` 增加 collect 分支，用新的 `QrCameraCollector`（`LinkedHashSet` + 50 上限）过滤重复载荷并持续回调；单码模式仍保留“首个结果即停止”的原语义。
- ViewModel 增加内存态 `cameraResults`：`startCameraScan` 清空会话，`onCameraDecoded` 在收集模式下追加，`stopCameraScan`（按钮文案在多码模式下改为“完成”）把 1 个结果放入原结果卡、2 个及以上放入结果列表；未收集到任何结果时仅关闭预览并保留上一份结果。扫描期间多码开关禁用，`setMultiDecode` 仍会兜底结束会话。
- Android Studio JBR 直接执行已编译类：72 项检查覆盖去重、重复项不改变数量、空载荷拒绝、自定义上限（3）与默认上限（50）的边界、超限拒绝、Unicode 与 `MECARD:`/`tel:` 载荷，全部通过。
- 离线 `:app:assembleDebug :app:lintDebug` 成功（1 分 16 秒），0 错误、24 条既有警告、0 hint，二维码源码无 Lint 问题；`qr_*` 中英各 138 个 key 对齐且引用齐全。
- 未运行真机/模拟器：连续移动相机时的收集节奏、重复码误判、快速进出预览、低内存与取消响应、窄屏下计数与按钮布局仍待验收；APK 17,644,167 字节。

以下保留多码识别与更早的修复记录：

2026-10-04，多码识别（相册）：

- 阅读 ZXing 3.5.3 `QRCodeMultiReader` 与 `MultipleBarcodeReader` 源码，新增 `QrMultiDecoder` 作为纯组件（`decodeMultiple` + 去重 + 空结果），`LocalQrRepository` 在 multi 模式下先走多码检测、未命中再回退单码路径。
- 领域新增 `QrDecodeResult.Multiple(texts)`；`QrRepository.decode`/`DecodeQrUseCase` 增加 `multi` 参数；ViewModel 增加 `multiDecode`（SavedState 恢复）与 `decodeTexts`，切换开关或清空都会丢弃旧结果；页面新增开关、说明与多码结果卡（逐条复制、复制全部、分享全部）。
- Android Studio JBR 直接执行已编译类：9 项检查覆盖三码同图（3 个载荷全部命中）、单码、重复载荷去重、空白图片无结果、纯条码在多码模式下返回空（由单码路径接管）、二维码与条码混合图片只返回二维码、共享提示集合与极小帧，全部通过。
- 离线 `:app:assembleDebug :app:lintDebug` 成功（1 分 19 秒），0 错误、24 条既有警告、0 hint，二维码源码无 Lint 问题；`qr_*` 中英各 135 个 key 对齐且引用齐全。
- 未运行真机/模拟器：多码识别耗时、低内存与取消响应、相机扫描与多码开关的交互、结果列表在窄屏/放大字号下的布局仍待验收；APK 17,402,791 字节。

以下保留专用内容类型与更早的修复记录：

2026-10-04，专用内容类型：

- 新增电话、邮箱、短信、联系人和日历事件 5 种生成类型，生成侧共 7 类；`QrContentType`、`QrInputPolicy`、`QrUiState.form`、`QrInput`、ViewModel 保存状态与页面表单同步扩展，空状态改由 `QrInputPolicy.hasContent` 统一判断。
- 参考 ZXing 3.5.3 的 `MECARDContactEncoder`/`ContactEncoder`/`VCardContactEncoder` 源码：采用 MECARD 字段顺序与转义规则，放弃 vCard 与电话类型元数据；事件采用 iCalendar VEVENT 本地浮动时间；电话/短信/邮箱分别使用 `tel:`、`SMSTO:`、`mailto:`。
- Android Studio JBR 直接执行已编译类：43 项检查覆盖 7 种内容类型的空状态、电话/邮箱/短信格式拒绝、MECARD 转义与换行移除、iCalendar 转义、STRICT 日期时间拒绝（`2026-02-30`、`25:00`）、结束早于开始、半填结束时间，以及 MECARD/VEVENT/tel 载荷的真实 QR 编码往返，全部通过。
- 两轮离线 `:app:assembleDebug :app:lintDebug`：首轮发现并修复 1 条 `ModifierParameter` 与 2 条 `TypographyDashes`（日期占位符改为 `YYYY-MM-DD`），最终 1 分 32 秒，0 错误、24 条既有警告、0 hint，二维码源码无 Lint 问题；`qr_*` 中英各 129 个 key 对齐且引用齐全。
- 未运行真机/模拟器或第三方扫描器：MECARD/VEVENT 在具体扫码应用中的导入行为、浮动时间解释、`tel:`/`SMSTO:`/`mailto:` 的应用跳转样式、以及大字号/窄屏下 7 个类型胶囊与日期时间并排输入框的布局仍未验收。

以下保留相机扫描与更早的修复记录：

2026-10-04，相机扫描与条码支持：

- 新增 CameraX 1.6.2（core/camera2/lifecycle/view）与 `CAMERA` 清单权限、`camera.any` 非必需特性；相机权限只在扫描时按需申请。相机扫描与相册解析共用 `QrBarcodeFormats` 的 13 种符号体系，相册识别从 `QRCodeReader` 换成 `MultiFormatReader`。
- 帧几何拆成可独立验证的 `QrFrameLuminance`（Y 平面去 stride 拷贝、90°/270° 旋转、半转复用）。Android Studio JBR 直接执行已编译类：2 个 Y 平面样例（紧密与带填充）、6 个方向样例、8 次真实条码往返（中文/emoji 二维码与 CODE 128 各含 0°/90°/180°/270° 传感器方向）、13 种格式清单，全部通过；同时验证非法帧（目标过小、stride 小于宽度、数据截断、超过 4M 像素、空尺寸）被拒绝。
- 桌面样例只用 `java.nio.ByteBuffer` 与 ZXing，不覆盖 Android `ImageProxy`、CameraX 绑定、权限对话框、预览显示、手电筒和真机识别率。
- 离线 `:app:assembleDebug :app:lintDebug` 成功（2 分 20 秒，随后一次增量确认），0 错误、24 条既有警告，与引入相机前的警告数一致；Lint 未报告二维码源码问题，新增的 `AutoboxingStateCreation` 提示已改为 `mutableIntStateOf`。相机依赖带来 `libimage_processing_util_jni.so`、`libsurface_util_jni.so` 原生库，构建日志提示无法剥离调试符号并按原样打包。
- 构建产物：`android/app/build/outputs/apk/debug/app-debug.apk`，17,359,669 字节，比引入相机前的 12,753,499 字节增加约 4.6 MB（CameraX 运行时与两个原生库）；未运行真机/模拟器、权限拒绝、后台切换、手电筒、相机占用或低内存验收。

以下保留结果导出与更早的修复记录：

2026-10-04，结果导出补齐：

- 两轮离线 `:app:assembleDebug :app:lintDebug` 成功，分别 1 分 33 秒、1 分 21 秒；最终 0 错误、24 条既有警告。新增 Bitmap KTX 与尺寸文案警告已修复，二维码源码/新增资源无 Lint 问题；79 个中英 `qr_*` 字符串 key 对齐且全部被引用。
- Android Studio JBR 直接调用已编译 `QrExportLayout`：QR 版本 1–40 × 三种尺寸共 120 个布局检查通过，覆盖模块整数比例、尺寸和额外留白中心位置，并拒绝不合法矩阵。
- 用同一布局通过 Java2D 绘制 PNG，再由 ZXing 解码：中文/emoji/多行、URL、转义 Wi-Fi 三个原文 × 四容错等级 × 四配色 × 三尺寸，共 144 个往返通过；PNG 像素无损、四模块留白和单文件预算符合预期。这是桌面 PNG/ZXing 样例，未调用 Android Canvas、Bitmap.compress、MediaStore、SAF 或 FileProvider，不能当作 Android 端导出验收。
- APK 为 `android/app/build/outputs/apk/debug/app-debug.apk`，12,753,499 字节；`git diff --check` 通过。本轮检查脚本在 `.tmp/qr-export`，完成后清理；未新增持久化自动回归测试，也未运行真机/模拟器、第三方分享或相册保存验收。

以下保留前轮修复记录：

- 三轮离线 `:app:assembleDebug :app:lintDebug` 均成功：第一轮 90 秒；补充读取预算复核/取消后的第二轮 121 秒；修正计数文案后的最终一轮 54 秒。
- 2026-10-04 多语言收尾后再次离线 `:app:assembleDebug :app:lintDebug` 成功（1 分 22 秒）；最终 0 错误、24 条警告，QR 源码/新增资源无 Lint 问题；`qr_*` 中英各 58 个 key 集合一致且全部被引用。
- 最终 Lint 为 0 错误、25 条警告，本轮开始为 27 条。最终报告未发现二维码源码/新增二维码资源的 Lint 问题；已有依赖版本、应用图标、其他工具文案/资源等警告仍在。Kotlin 首轮编译对沿用的 `LocalClipboardManager` 给出弃用提示，不属于 Lint 错误。
- `git diff --check` 通过；二维码未跟踪源码和本工具文档另做行尾空白静态检查，未发现结果。未进行功能样例检查。
- 未新增或运行自动化测试、样例运行、真机/模拟器或数据库联调；不将编译通过当作扫描成功或 Wi-Fi 连接验收。
- 构建 APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`，Lint 报告位于 `android/app/build/reports/lint-results-debug.html`，均在 D 盘项目。本轮 `.tmp/qr-fix` 临时目录已清理。已有固定 Gradle 缓存仍使用 `C:/Users/沧烁/.gradle`，本轮未迁移它。

## 9. 后续验收清单

- 中文、emoji、首尾空格、多行、超长粘贴、不完整 Unicode、各容错等级的容量不足与恢复。
- Wi-Fi 特殊字符及连续反斜杠在独立扫描器中的字段还原；开放/加密切换、隐藏网络、32 字节边界和目标网络连接；不把真实凭据写入验收日志。
- 清晰/模糊/反色/透明背景、不同旋转与镜像、极长全景、大图、小二维码、多码图片及不支持的文件。
- 快速选择 A/B、清空、取消、切换模式、返回、旋转、进程重建、失效 URI/provider 失败与低内存。
- 中英文、浅色/深色、窄屏/横屏、大字号、TalkBack、剪贴板、系统图片选择器和关闭系统动画。
- 相机：首次授权、拒绝后再次授权、系统设置中关闭权限后返回、无相机设备、相机被其他应用占用、前后台切换、旋转、手电筒开关与无闪光灯设备、低内存与大尺寸分析帧。
- 识别率：二维码/条码在各角度、近距离/远距离、暗光、反光、曲面与屏幕拍摄下的表现；CODE 39/ITF/Codabar 误识别样例；连续移动相机时是否只取第一次结果。
- 结构化内容：MECARD 在手机通讯录/第三方扫码应用中的导入结果（含转义字符与中文姓名）、VEVENT 在日历应用中的时间解释、`SMSTO:`/`mailto:`/`tel:` 是否被目标应用正确识别，以及 7 个类型胶囊在窄屏与放大字号下的滚动与换行。
- 事件扩展：本地浮动与转 UTC 在系统/第三方日历中的显示、跨时区导入、夏令时日期、提醒是否保留。
- 手动框选：触控点按精度、滑块范围、10% 最小选区、透明/旋转图片、预览内存与快速切换图片。
- 线性条码：各格式在独立扫码器/激光枪上的识读、屏幕与打印留白、窄条宽度、EAN/UPC 校验位与 Codabar 起止符兼容性。
- 导出与结构化载荷：PNG/JPEG 相册与文件保存、SVG 在浏览器/矢量软件/打印中的渲染与缩放、分享接收方、vCard 在系统通讯录中的多值导入、企业 Wi-Fi 在目标设备上的配置解析、邮件 cc/bcc 在邮件应用中的预填。
- 多码：含 2/3/5 个二维码的截图与照片、密集排布与倾斜拍摄、重复二维码、二维码与条码混合、超大图与低内存设备下的多码耗时、取消与重新选图，以及列表在窄屏与放大字号下的滚动。
- 相机收集：连续扫描 5 个以上二维码、同一码重复出现、码与码之间快速切换、收集满 50 个、扫描中切后台/旋转/退出、收集后复制与分享，以及计数与“完成”按钮在窄屏和放大字号下的布局。
- 符号名称：为 13 种格式各准备一个样本，核对结果卡与多码列表显示的格式名称与实际符号一致；相机与相册两条路径都要覆盖，并确认未知格式的兜底文案。
- 三种尺寸/四种颜色的 PNG 扫描、相册实际显示、API 26–28 创建文件、取消位置选择、provider 写入失败/删除失败、低空间/低内存、快速重复导出和旋转；第三方读取 FileProvider 图片、分享页取消/缺少接收方、长识别原文与重新生成后进程重建。

下一轮按二维码路线图补齐 CameraX 相机/常见条码扫描，继续维持按需权限、资源预算与单任务范围；完整功能与设备验收完成前不勾选二维码条目。
