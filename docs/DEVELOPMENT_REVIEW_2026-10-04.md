# 开发完成情况检查（2026-10-04）

## 2026-10-05 最新收尾

目录持久缓存、应用级去重刷新和本地浏览器/Android 16 基础联调已完成；修复网页 UTF-8/空输入旧结果/版本/转义/缓存及 WebView 来源/错误/导出。最终 141 个 Android 单元测试、24+12 项主机 SQLite 和 22 项网页回归通过，离线构建/Lint 成功（最终增量 47 秒，0 错误/22 既有警告），Nginx 检查和页面 200/no-cache 通过。用户要求加快后停止追加设备场景；原生 PNG/JPEG/SVG 导出、扫码历史、日历/通讯录/企业 Wi-Fi 导入等设备矩阵未完成，发布前补验，不继续阻塞功能开发。具体来源、边界和证据见 [本轮记录](WEB_TOOL_ACCEPTANCE_2026-10-05.md)。下文保留历史阶段状态。

## 结论

本轮其他 AI 已新增 10 个本地工具的代码、界面、资源、说明文档及 V4–V13 工具目录迁移。加上原有计算器，容器共注册 11 个工具。Android 和 Server 可以构建，但发现结果正确性、内存、主线程处理及版本兼容问题，尚不具备整批验收条件。

这批改动仍在未提交工作区，新增文件尚未纳入 Git；当前远端提交和 CI 不能证明这批改动已经验证。本次仅检查，并新增本报告，未修改业务代码、提交或推送。

## 后续修复进度

本报告下方的缺陷描述、行号及验证结果保留审查时的快照；后续改动以本节和对应工具文档为准。

2026-10-04，单位转换修复：

- 高优先级问题 1、结果问题 8：已修改代码。UseCase 在换算前后限制数值规模，格式化前估算普通字符串长度，长结果使用完整科学计数法；默认单位交换与界面使用同一套解析规则。
- 单位数量文档已改为 88 个；机械马力标签与因子对齐；交接资料已补充当前工作区和待修复范围。
- 修复后最终离线 `:app:assembleDebug :app:lintDebug` 构建成功（58 秒），Lint 为 0 错误、32 条警告，与审查时数量相同；`git diff --check` 通过。未新增或运行自动化测试，设备交互、数值边界及复制验收仍待执行。
- 当时其余审查问题尚未修复；图片压缩后续进度如下。

2026-10-04，图片压缩修复：

- 高优先级问题 2、结果问题 9–11：已完成代码修复。增加解码前预算、文件输出、旧任务取消/版本检查、真实编码文件预览，以及旧系统的文件创建保存流程。
- 图片页加载、本轮硬编码文案、主线程分享文件写入、取消被当作失败、来源大小未知时统计不准确等问题同步修复。
- 补充发现并修复尺寸探测的 null 误判：原代码把 `inJustDecodeBounds` 正常返回 null 当作打开失败；该分支按 API 语义更正。
- 依据实际 GitHub Compressor 源码补充参考与适配说明；新增工具开发约定并接入两处 AGENTS。恢复二维码路线图条目为未完成，不代表二维码功能已修复。
- 最终离线 `:app:assembleDebug :app:lintDebug` 成功（65 秒），Lint 为 0 错误、27 条警告，相比本轮开始的 32 条减少 5 条；`git diff --check` 通过。本轮没有新增或运行自动化测试、真机、模拟器或数据库联调，设备验证仍待执行。
- 剩余主要问题为二维码、文本正则、JSON、URL、日期及其他页面多语言/架构问题；继续按单工具控制范围。

2026-10-04，二维码修复：

- 高优先级问题 3、Wi-Fi 转义和二维码多语言问题：已完成本轮代码修复。UI 改为提交 URI，Repository 在后台探测尺寸、按内存/像素预算采样后识别；增加流读取预算、结果归属、取消/重试和串行互斥，避免旧任务覆盖或并行读大图。
- 增加 Wi-Fi 五类特殊字符转义、开放网络密码省略和隐藏网络控制；密码默认遮盖且不写入 SavedState。恢复 ZXing 四模块留白，生成后台执行，错误/配色本地化并使用共享加载。
- docs/QR_TOOL.md 已补充实际阅读的 ZXing 3.5.3 与 JourneyApps Decoder 源码、采用规则、适配差异、明确资源限制及未实现功能。
- 三轮离线 `:app:assembleDebug :app:lintDebug` 均成功，最终一轮 54 秒，Lint 为 0 错误、25 条警告，本轮开始为 27 条；二维码源码/新增资源无 Lint 问题。`git diff --check` 和二维码源码/文档行尾空白检查通过，本轮临时目录已清理。未新增或运行自动化测试、样例、设备或数据库联调；扫码效果、Wi-Fi 扫码连接和并发交互仍待验收。
- 二维码路线图保持未完成：相机/条码、其余内容类型及完整结果导出仍有缺口。下一步处理文本正则，再处理 JSON、URL、日期及其他剩余问题。

2026-10-04，文本工具修复：

- 高优先级问题 4：已完成本轮代码修复。采用实际查阅的 RE2/J 1.8，用户模式不再由主线程 Java/Kotlin 回溯正则执行；统计、转换、替换经 suspend UseCase 进入后台 Repository，使用互斥、取消和请求版本检查。
- 增加输入/输出、模式长度、嵌套、计数展开、程序规模和分组预算；在输出追加前检查完整长度。引擎输入读取和自有循环协作检查 2 秒预算，文档明确同步编译等步骤并非硬实时中断。
- 补齐多行锚点、dotAll、分组模板、匹配数量和合法空结果；修复长数字自然排序的 Long 溢出行为，并明确码点/Han/换行统计、Locale.ROOT 与原文替换语义。
- docs/TEXT_TOOLS.md 已记录实际阅读的 RE2/J 与 IT-Tools 文件、源码版本/链接、适配差异、语法不支持项、资源限制和待验收清单；RE2/J 许可证随 APK 打包。
- 三轮 Android 构建/Lint 均成功，最后两轮离线，最终 58 秒、0 错误、27 条警告；文本源码/新增资源无 Lint 问题。75 个中英文文本 key 集合相同且无重复，差异/行尾空白检查通过，本轮临时目录已清理。27 为最终报告实际数量，不声称零警告或用数量差值判定文本引入问题。
- 未新增或运行自动化测试、正则样例、设备、Preview 渲染或数据库联调；RE2/J 子集兼容、取消响应、极限输入及剪贴板等仍待运行时验收。下一步修复 JSON，再处理 URL、日期及其他剩余问题。

2026-10-04，JSON 工具修复：

- 高优先级问题 5：已完成本轮代码修复。移除宽松 JSONTokener 路径，使用有界语法扫描与空白改写，保留合法根字符串的引号；所有六种根类型使用统一完整值处理。
- 数字不转 Long/Double/BigDecimal，保留大整数、小数、指数及原始字符串片段；全部重复成员按原顺序输出，按解码键统计重复次数并提示，不覆盖用户数据。
- 转义生成完整带引号 JSON 字符串；反转义明确区分完整字符串和转义内容，保留内容首尾空白，拒绝未知/不完整转义和不成对代理字符。文档说明 Unicode 策略比标准 ABNF 更严格。
- 四个 UseCase/Repository 操作改为 suspend 后台执行，加入互斥、取消/请求版本、64 层嵌套、50000 项结构、输入/输出及协作时间预算。UI 复用共享加载，显示中英错误类型/位置与合法空结果。
- docs/JSON_TOOL.md 补充实际取得的 CyberChef、IT-Tools、Lottie reader 源码与 RFC 来源、适配差异、功能/边界覆盖和剩余验收。V10 未改，没有新增依赖、权限或 API。
- 三轮离线 `:app:assembleDebug :app:lintDebug` 均成功，最终 55 秒，Lint 为 0 错误、25 条警告，本轮开始为 27 条；JSON 源码/相关资源无 Lint 问题。54 个中英文 JSON key 集合相同且无重复、引用齐全；差异/行尾空白检查通过，本轮临时目录已清理。
- 未新增或运行自动化测试、业务样例、设备、Preview 渲染或数据库联调；严格语法覆盖、精度保留、空白/Unicode、预算、状态恢复与剪贴板仍需运行时验收。下一项修复 URL，再处理日期、UUID、Base64 及其他剩余问题。

2026-10-04，URL 编解码修复：

- 结果与兼容性问题 6：已完成本轮代码修复。移除 `java.net.URLEncoder` / `URLDecoder` 的表单语义混用，改为自实现严格单层百分号编解码；组件模式按 RFC 3986 非保留字符集，`a!()*'~` 正确编码为 `a%21%28%29%2A%27~`。
- 新增“组件 / URI 整体 / 表单值”三种类型在编码和解码两侧一致生效：URI 整体保留 `+` 并保留已转义的 URI 结构符，`?q=a+b` 往返不变；表单值按 WHATWG 规则处理 `+` 与空格，%2B 还原为加号。
- 增加严格的 %HH 与 UTF-8 校验（含过长编码、代理区、截断字节、未配对代理字符）、错误位置、单层解码语义、输入/输出与时间预算、后台互斥执行、请求版本、取消/重试、结果预览截断提示和中英反馈；复用共享加载组件。
- 参考实际取得的 WHATWG URL Standard、jsdom/whatwg-url、CyberChef URLEncode/URLDecode 与 IT-Tools url-encoder 源码；docs/URL_CODEC.md 已记录版本/链接、采用思路、适配差异、功能与边界、人工样例及未验收范围。
- 三轮离线 `:app:assembleDebug :app:lintDebug` 均成功；最终完整构建 83 秒、增量确认 15 秒，Lint 为 0 错误、24 条警告（本轮开始为 25 条）；URL 源码/新增资源无 Lint 问题，44 个中英 URL key 对齐且引用齐全；`git diff --check` 通过。唯一编译提示为项目既有的 `LocalClipboardManager` 弃用告警。
- 未新增或运行自动化测试、业务样例、设备、Preview 渲染或数据库联调；加号往返、表单/整 URI 差异、错误位置、长文本、取消响应和剪贴板仍需运行时验收。下一项处理日期严格解析，再处理 UUID、Base64 及剩余多语言/架构问题。

2026-10-04，时间戳严格日期解析修复：

- 结果与兼容性问题 7：已完成本轮代码修复。日期时间解析由默认 SMART 改为 `ResolverStyle.STRICT` + `uuuu` 模式，`2026-02-30` 不再被静默改写为 `2026-02-28`；`2025-02-29`、`1900-02-29`、13 月、24 时、60 分均被拒绝。
- 新增 `TimestampResult.InvalidDate` 与 `TimestampDateError`，界面区分“格式无效 / 日期或时间不存在 / 超出支持范围”并补中英文案；年份限制统一为 1900–3000。
- 夏令时春季缺口（America/New_York 2026-03-08 02:30）返回“日期或时间不存在”，不再自动后移；秋季重叠取较早偏移，文档明确该选择。
- docs/TIMESTAMP.md 已重写，记录实际参考的 IT-Tools date-time-converter、CyberChef From/To UNIX Timestamp、OpenJDK ResolverStyle/DateTimeFormatter 源码与适配差异、功能边界和人工验收样例。
- 修复后用 Android Studio 自带 JBR 运行临时验证程序（已删除）：STRICT + `uuuu` 拒绝上述非法值，SMART + `yyyy` 复现原缺陷，LENIENT 可识别形状用于错误分类，闰年与夏令时缺口/重叠行为符合预期。
- 离线 `:app:assembleDebug :app:lintDebug` 成功（1 分 26 秒），0 错误、24 条警告，数量与修复前一致；时间戳源码/新增资源无 Lint 问题，30 个中英 `timestamp_*` key 对齐且全部被引用；`git diff --check` 通过。
- 未新增或运行自动化测试、Android 运行时样例、设备、Preview 或数据库联调；下一步处理 UUID 分层，再处理 Base64 非 UTF-8 策略及剩余多语言/架构问题。

2026-10-04，UUID 分层修复：

- 架构一致性问题：已完成代码修复。移除 ViewModel 内的 `UUID.randomUUID()` 与字符串拼接，生成和格式化统一经 `GenerateUuidUseCase → UuidRepository / LocalUuidRepository`；Repository 拆分 `generateRaw` 与 `format`，ViewModel 只持有原始列表与 SavedState 选项。
- 初始 UI 状态也按已保存选项格式化，恢复时不再闪现默认小写连字符格式；数量 clamp 保留在 ViewModel 与 Repository 两层。
- docs/UUID_GENERATOR.md 已重写，记录实际参考的 OpenJDK UUID.java、uuid-creator UuidCreator/RandomBasedFactory 与 RFC 9562 v4 规则、适配差异、边界和人工样例。
- 离线 `:app:assembleDebug :app:lintDebug` 成功（1 分 16 秒），0 错误、24 条警告，UUID 源码无 Lint 问题；`uuid_*` 中英各 16 个 key 对齐且全部被引用；JBR 临时验证 10,000 个 UUID 全部为 v4/variant 2、格式正确且无重复；`git diff --check` 通过。
- 未新增或运行自动化测试、Android 运行时样例、设备、Preview 或数据库联调；下一步处理 Base64 非 UTF-8 策略及剩余多语言/架构问题。

2026-10-04，Base64 非 UTF-8 策略修复：

- 结果与一致性问题（Base64 非 UTF-8 被静默替换为 U+FFFD）：已完成代码修复。`Base64Result` 拆分为 `Encoded` 与 `Decoded`；解码先得到字节，按 RFC 3629 逐字节严格校验 UTF-8，再用 `CharsetDecoder` 的 REPORT 模式生成文本。
- 合法 Base64 但非法 UTF-8 时不再输出替换字符：界面显示总字节数、无效字节数与首个无效位置（1 起算），禁用文本复制与互换；解码成功后始终提供无损 Hex 视图，预览最多 8000 字符、复制使用完整 Hex。
- docs/BASE64_CODEC.md 已重写，记录实际参考的 CyberChef FromBase64/Base64.mjs、IT-Tools base64-string-converter/base64.ts/base64-file-converter、产品数据契约、适配差异和人工样例。
- 离线 `:app:assembleDebug :app:lintDebug` 成功（完整构建 1 分 29 秒；修正预览文案后 1 分 40 秒；收紧防御分支后最终增量 1 分 10 秒），最终 0 错误、24 条警告，Base64 源码与新增资源无 Lint 问题；`base64_*` 中英各 25 个 key 对齐且全部被引用；`git diff --check` 通过。
- JBR 直接调用编译后的 Kotlin 类，最终代码复验 20/20 项通过（`/w==`、hello、中文往返、URL-Safe 编码与回退解码、过长编码、孤立继续字节、BOM、非法输入与空输入）；临时程序已删除。
- 未新增或运行自动化测试、Android 运行时样例、设备、Preview 或数据库联调；下一步处理二维码剩余硬编码文案的多语言问题，再做 Phase 2 收尾验收。

2026-10-04，二维码多语言收尾：

- 一致性问题（英语界面不完整）：已完成本轮代码修复。QR 代码中的 WPA/WPA2、WEP 安全类型标签与 L/M/Q/H 容错等级标签移入中英资源（协议标记保留标准原文、两端同值）；`QrErrorCorrection` 不再携带展示字符串，界面通过资源映射显示。
- QR feature 再次扫描未发现用户可见硬编码字面量；颜色名称、错误、加载与复制文案均已资源化。此前报告中的 `QrColorStyle.displayName` 中文硬编码与英文分享文案在当前代码中已不存在（分享标题使用 `image_compress_share_title` 资源）。
- 离线 `:app:assembleDebug :app:lintDebug` 成功（1 分 22 秒），0 错误、24 条警告，QR 源码/新增资源无 Lint 问题；`qr_*` 中英各 58 个 key 对齐且全部被引用。
- 未新增或运行自动化测试、设备或 Preview；Phase 2 剩余设备/语言/剪贴板/二维码扫描验收与自动化回归测试缺口，数据库/API 运行时联调已在下一节完成。

2026-10-04，Phase 2 数据库/API 运行时联调：

- 启动 Docker Desktop Linux 引擎（29.8.1），五个本地容器全部健康；从当前工作区重建 server 镜像，替换只含 V1–V3 的旧镜像。
- Flyway 执行 V4–V13 共 10 个迁移（00:00.053s），schema 从 v3 升到 v13，13 条历史记录全部 `success=1`。
- `GET /api/v1/tools` 返回 11 个工具（calculator + 10 个 Phase 2 工具），DEV 分类 6 个；`page=3&pageSize=5` 返回 1 条、total=11；非法 `pageSize=0` 返回 HTTP 400。
- Nginx 代理（8088）返回相同目录结果；官网 `/` 200、`/admin/` 200、`/admin` 308、健康检查直连/代理均 200。
- Android `ToolCatalogDto` 字段与响应键一致；11 个内置 ToolDefinition code 与接口返回 code 一致。
- 未执行自动化测试、Android JSON/网络/缓存接入、设备/语言/剪贴板/二维码扫描验收；Phase 2 仍不满足“全部验收通过”。

2026-10-04，页面切换动画统一：

- 新增 `core/ui/ToolboxMotion.kt` 作为唯一页面动画规范；显式加入 Compose Animation 依赖（版本由现有 BOM 管理，未新增独立版本）。
- `HomeRoute` 的首页/搜索/工具详情切换改为 `AnimatedContent`：前进使用淡入 + 自末端滑入（240ms），返回反向，其他情况交叉淡化；`HomeScreen` 四个底部标签改为 180ms 交叉淡化，不再瞬时切换。
- 扫描确认页面代码没有其他自定义 enter/exit 动画；工具内部加载继续使用共享 `ToolboxLoading`，页面切换规范见 docs/ANDROID_UI_SPEC.md 与 PROJECT_SPEC Decision 020。
- 离线 `:app:assembleDebug :app:lintDebug` 成功（2 分 48 秒，全量执行），0 错误、24 条警告，无动画相关 Lint 问题。
- 未执行设备/模拟器、关闭系统动画、TalkBack、低端设备或快速连续切换验收。

2026-10-04，移除页面特性宣传语：

- 移除 11 个工具页顶部的“本地处理 · 无需登录或联网”类标签，以及首页 Hero 的“本地优先 · 轻量使用”标语和工具卡片的运行模式徽标。
- 搜索、收藏、我的、二维码解析说明中的“离线/无需登录”措辞改为中性功能描述；工具卡片描述去掉“本地计算/离线处理/保护隐私”等收尾措辞，URL 工具的“正在本地处理/本地处理失败”改为中性状态文案；中英资源同步删除或改写。
- `ToolMode` 枚举继续作为目录/执行模式数据保留，只移除界面映射与展示，不影响注册中心、API 或路由逻辑。
- Android UI 规范新增“禁止特性宣传语”规则，首页、单位转换、时间戳、UUID、Hash 文档同步更新。
- 离线 `:app:assembleDebug :app:lintDebug` 成功（前置完整构建 2 分钟，最终资源增量 1 分 3 秒），0 错误、24 条警告；移除的资源没有残留引用，Lint 未新增问题；未执行设备与语言切换验收。
- 服务端 V3–V13 种子里的中文描述仍包含旧措辞；客户端当前显示内置资源，远程目录合并前需用新的数据迁移统一描述（不修改已执行迁移）。

2026-10-04，服务端目录描述统一：

- 新增 `V14__normalize_tool_descriptions.sql`，按 code 和二进制精确原始描述替换 11 个工具默认说明，移除宣传措辞并对齐 Android 中文资源；Base64、URL、文本统计说明同时与修复后功能一致。V1–V13 文件未修改。
- 会话临时表验证 9 条默认文案更新、自定义计算器文案及带尾空格的 UUID 文案保留，关闭/软删除/版本/推荐/排序保持原值；临时表没有改写真实目录。
- Server 镜像构建成功（Maven package 3.448 秒，测试跳过），本地 API 更新后 Flyway 执行 V14 成功（00:00.027s），schema v14；14 条历史全部 `success=1`，V1–V13 校验值与执行前相同。
- 数据库 11 条描述的宣传措辞计数为 0；直连/代理返回一致，每条描述与 Android 中文资源相同，其他公开元数据与迁移前相同；健康状态 UP，五个容器健康。
- 数据库、目录、API 示例、项目规格、路线图和交接说明已同步；临时基线文件清理。未新增持久化自动测试，未执行 Android 构建或设备验收。剩余二维码功能与设备/多语言/剪贴板/动画验收、自动化回归缺口继续按 Phase 2 推进。

2026-10-04，二维码结果导出补齐：

- 阅读 ZXing 3.5.3 的 `QRCodeEncoder.encodeAsBitmap`、`EncodeActivity.share` 实际源码及 Android MediaStore/SAF/FileProvider 资料，在现有架构内新增 SaveQrImageUseCase / PrepareQrShareUseCase → QrExportRepository。
- PNG 三种尺寸（512/1024/2048，默认 1024）、整数模块绘制、保留四模块留白；API 29+ Pending 相册发布、API 26–28 CreateDocument 保存；独立随机缓存文件、FileProvider 图片分享和失败/取消清理。
- 识别完整原文可分享与重新生成；转入生成页不自动把识别原文写 SavedState，超长/非法 Unicode 拒绝而不截断。导出快照、重复操作互斥、共享加载、双语反馈和一次性分享请求消费已接入。
- 两轮 Android 离线构建/Lint 成功（93 秒、最终 81 秒），最终 0 错误、24 条既有警告，二维码源码/新增资源无 Lint 问题；79 个中英字符串 key 对齐且引用齐全。
- JBR 直接检查已编译布局 120 个样例通过，Java2D PNG/ZXing 144 个往返通过；不覆盖 Android Canvas、相册、SAF、FileProvider 和真实接收方。未新增持久化自动回归测试，临时检查文件清理。
- 二维码条目继续未勾选：下一项相机/常见条码，其他专用类型和设备/功能验收仍待完成。

2026-10-04，二维码相机扫描与条码支持：

- 按“先读源码再实现”的约定，阅读 CameraX ImageAnalysis 官方文档、JourneyApps SourceData/Decoder/DefaultDecoderFactory 与 ZXing PlanarYUVLuminanceSource 实际源码，记录采用的思路与适配差异。
- 引入 CameraX 1.6.2 四个组件；清单声明 CAMERA 与非必需 camera.any；相机权限只在扫描时按需申请，QrToolDefinition.requiredPermissions 保持为空以免拦截整个工具。
- 新增 QrCameraAnalyzer（Y 平面去 stride、90°/270° 转正、半转复用、TRY_HARDER、KEEP_ONLY_LATEST、单线程执行）与 QrCameraScanner（权限/无相机/启动失败三态、生命周期绑定、手电筒、重试重建预览）；相册解析改用 MultiFormatReader，与相机共用 13 种符号体系。
- 离线 assembleDebug/lintDebug 成功（2 分 20 秒全量与 1 分 32 秒确认），0 错误、24 条既有警告、0 hint，二维码源码无 Lint 问题；93 个中英字符串 key 对齐且引用齐全。
- JBR 对已编译类验证：2 个 Y 平面样例、6 个方向样例、8 次条码往返、13 种格式清单通过，非法帧被拒绝；未覆盖 Android ImageProxy/CameraX 绑定/权限对话框/预览/手电筒。
- APK 增至 17,359,669 字节（CameraX 原生库约 +4.6 MB）。真机识别率、权限拒绝、后台切换与低内存验收仍待执行；二维码条目继续未勾选，下一个缺口是多码、专用内容类型与设备验收。

2026-10-04，二维码专用内容类型：

- 按“先读源码再实现”的约定，阅读 ZXing 3.5.3 的 `MECARDContactEncoder`、`ContactEncoder`、`VCardContactEncoder`，采用 MECARD 字段顺序与转义规则（转义反斜杠/冒号/分号、移除换行、末尾补分号），放弃 vCard 与电话类型元数据。
- 新增电话、邮箱、短信、联系人和日历事件 5 种生成类型；载荷分别为 `tel:`、`mailto:`、`SMSTO:`、MECARD、VEVENT。事件使用本地浮动时间，日期 `uuuu-MM-dd` 与时间 `HH:mm` 采用 STRICT 解析，年份 1900–3000，结束时间可选但必须晚于开始。
- 领域层集中在 `QrInputPolicy`：`hasContent` 决定空状态，`validate` 返回 `INVALID_PHONE`/`INVALID_EMAIL`/`INVALID_SMS`/`INVALID_CONTACT`/`INVALID_EVENT`/`EVENT_RANGE` 等分类；字段上限 256、短信正文 500、日期 10、时间 5，表单值随 SavedState 恢复。
- 离线 assembleDebug/lintDebug：首轮发现并修复 1 条 `ModifierParameter`、2 条 `TypographyDashes`（日期占位符改为 `YYYY-MM-DD`），最终 1 分 32 秒，0 错误、24 条既有警告、0 hint，二维码源码无 Lint 问题；`qr_*` 中英各 129 个 key 对齐且引用齐全。
- JBR 对已编译类执行 43 项检查：7 种类型的空状态、电话/邮箱/短信格式拒绝、MECARD 与 iCalendar 转义、STRICT 日期时间拒绝、结束早于开始、半填结束时间，以及 MECARD/VEVENT/tel 载荷的真实 QR 编码往返，全部通过。
- 未覆盖真机、第三方扫码器的 MECARD/VEVENT 导入、浮动时间解释与 `tel:`/`SMSTO:`/`mailto:` 跳转样式；APK 17,402,063 字节。二维码条目继续未勾选，剩余缺口是多码列表、vCard/多号码、事件时区与设备验收。

2026-10-04，二维码多码识别（相册）：

- 按约定阅读 ZXing 3.5.3 `QRCodeMultiReader` 与 `MultipleBarcodeReader` 源码，新增纯组件 `QrMultiDecoder`（`decodeMultiple`、去重、空结果即未命中）；`LocalQrRepository` 在 multi 模式下先多码检测、未命中回退单码路径，保证图里只有条码时仍可识别。
- 领域新增 `QrDecodeResult.Multiple`；仓库与用例增加 `multi` 参数；ViewModel 增加 SavedState 恢复的 `multiDecode` 与内存中的 `decodeTexts`，切换开关或清空即丢弃旧结果；页面新增开关、说明与多码结果卡（逐条复制、复制全部、分享全部），单结果仍走原结果卡。
- 离线 assembleDebug/lintDebug 成功（1 分 19 秒），0 错误、24 条既有警告、0 hint，二维码源码无 Lint 问题；`qr_*` 中英各 135 个 key 对齐且引用齐全。
- JBR 对已编译类执行 9 项检查：三码同图全部命中、单码、重复去重、空白无结果、纯条码在多码模式返回空、二维码与条码混合只返回二维码、共享提示集合与极小帧，全部通过；未覆盖 Android 图片加载、真机耗时、取消响应与布局。
- 多码模式仅覆盖二维码；同一图片的多个条码/混合多码、相机连续收集仍待实现。APK 17,402,791 字节，二维码条目继续未勾选。

2026-10-04，相机多码收集：

- `QrCameraAnalyzer` 增加 collect 分支与新的 `QrCameraCollector`（`LinkedHashSet`、去重、单次上限 50），收集模式下持续回调新载荷而不在首个结果后停止；单码模式语义不变。
- ViewModel 增加内存态 `cameraResults`：开始扫描清空、收集模式追加、停止时 1 个结果进原结果卡、2 个及以上进结果列表、空会话保留旧结果；`QrCameraScanner` 新增 `collect`/`collected` 参数，预览显示已收集数量并把按钮文案改为“完成”；扫描期间多码开关禁用，`setMultiDecode` 兜底结束会话。
- 离线 assembleDebug/lintDebug 成功（1 分 16 秒），0 错误、24 条既有警告、0 hint，二维码源码无 Lint 问题；`qr_*` 中英各 138 个 key 对齐且引用齐全。
- JBR 对已编译类执行 72 项收集器检查（去重、重复项不改数量、空载荷、自定义上限 3 与默认上限 50 的边界、超限拒绝、Unicode 与 `MECARD:`/`tel:` 载荷），全部通过；未覆盖真机收集节奏、误判、低内存与布局。
- 同一图片的多个条码/混合多码仍待实现。APK 17,644,167 字节，二维码条目继续未勾选。

2026-10-04，识别结果符号名称：

- 新增 `QrSymbology` 与 `QrDecodeEntry(text, symbology)`；`QrDecodeResult.Success`/`Multiple` 改为携带条目，`QrBarcodeFormats.symbologyOf` 集中映射 ZXing `barcodeFormat`，`UNKNOWN` 兜底未报告或不支持的格式。
- 相机分析器、单码路径与多码路径统一返回条目；UiState 改为 `decodeEntry`/`decodeEntries`，相机会话收集列表同样保存条目；界面在结果卡与多码列表显示符号名称，协议名中英同值，仅“未知格式”翻译。
- 离线 assembleDebug/lintDebug 成功（1 分 21 秒），0 错误、24 条既有警告、0 hint，二维码源码无 Lint 问题；`qr_*` 中英各 152 个 key 对齐且引用齐全。
- JBR 对已编译类执行 27 项检查：13 种格式映射、`null`/MAXICODE/RSS_14 回退、QR/Code 128/Code 39/EAN-13 单码与多码的真实格式标注，全部通过；未覆盖真机布局与真实样本核对。
- APK 17,647,987 字节，二维码条目继续未勾选。

2026-10-04，邮件头部字段与事件纪要：

- 领域新增 `QrFormInput.emailSubject`/`emailBody` 与 `QrEventInput.description`；邮箱按 RFC 6068 生成 `?subject=`/`&body=` 并用百分号编码（只保留 `A-Za-z0-9-._~`），事件新增 `DESCRIPTION` 行并允许纪要换行（其余字段仍拒绝控制字符）。
- 校验补充：邮件主题 256、正文 500，事件纪要 500，超限返回 `INPUT_TOO_LONG`；UTF-8 检查覆盖新字段；空主题/正文不产生查询串，空纪要省略 `DESCRIPTION`。页面新增三个字段（正文与纪要支持多行），随 SavedState 恢复，示例按钮一并填入。
- 离线 assembleDebug/lintDebug 成功（1 分 51 秒），0 错误、24 条既有警告、0 hint，二维码源码无 Lint 问题；`qr_*` 中英各 158 个 key 对齐且引用齐全。
- JBR 对已编译类执行 20 项检查：纯地址、仅主题、主题+正文的编码往返（中文/emoji/`&`/`#`/换行）、未编码字符、主题与正文超限、错误地址、空纪要省略 `DESCRIPTION`、多行纪要转义、纪要超限与控制字符拒绝、mailto/VEVENT 真实二维码往返，全部通过；未覆盖第三方邮件/日历应用的实际预填与显示。
- APK 17,652,879 字节，二维码条目继续未勾选；剩余缺口为同一图片的多个条码/混合多码、vCard/多号码、事件时区与提醒、设备验收。

2026-10-04，线性条码分带多码检测：

- 新增 QrBandDecoder（三条重叠横向分带、去重、单次全图量级成本），参考 ZXing ByQuadrantReader 裁剪重试思路并说明为何不采用 GenericMultipleBarcodeReader 的递归实现；LocalQrRepository 的 multi 顺序改为 QR 多码 → 分带 → 单码回退。
- JBR 对已编译类执行 10 项检查（单条码、三条带、跨带边界、重复折叠、空白、纯二维码、小尺寸帧）全部通过；离线 assembleDebug/lintDebug 成功（1 分 30 秒），0 错误、24 条既有警告、0 hint；未覆盖真机识别率与竖向条码。

2026-10-04，竖向与混合多码：

- QrBandDecoder 增加竖向分带（先转置再交给按行扫描的 1D 读码器）；相册多码顺序改为 QR 多码 → 分带 → 单码回退，QR 与分带结果按文本去重合并，一个二维码加若干条码的图片可一次列全；两个以上二维码时跳过分带以控制耗时。
- JBR 已验证 10 项（三条横向、三条竖向、横竖混合、跨方向去重、空白、小尺寸帧）；首次运行暴露竖向条带未转置的问题，修正后复验通过。离线 assembleDebug/lintDebug 成功（1 分 17 秒），0 错误、24 条既有警告、0 hint；未覆盖真机竖向识别率与分带耗时。

2026-10-04，WEB 模式契约层（Decision 019）：

- Android ToolMode 新增 WEB；DTO 按枚举名映射，无需额外分支；Server ToolResponse 的 OpenAPI 允许值加入 WEB；新增 V15__allow_web_tool_mode.sql 放宽 ck_tool_definition_mode（V1 不动，只改约束）。
- Android 离线 assembleDebug/lintDebug 通过（1 分 24 秒），0 错误、24 条既有警告；Docker 重建 server 镜像后 Flyway 实际执行 V15（00:00.133s），schema 升到 v15、15 条历史全部 success=1，CHECK 约束已含 WEB，目录仍为 11 个工具、0 个 WEB。
- 文档已同步：API.md 的 mode 允许值、DATABASE.md 的迁移表与 mode 说明、TOOL_MODEL.md 的模式清单、PROJECT_SPEC Decision 019 的契约状态。
- 仍未实现：WebView 容器（加载/离线/错误/重试、返回键回退、域名白名单、隐私提示）、客户端 WEB 工具打开路径、首个网页版工具页面与 Nginx /tools/ 托管；数据库迁移尚未执行。

2026-10-04，WebView 容器（Decision 019 第 2 步）：

- 新增 core/ui/ToolboxWebView.kt：ToolboxWebScreen 容器与 ToolboxWebTools（基础地址、/tools/<code>/ 拼装、允许域名集合）；加载中复用共享 ToolboxLoadingState，主框架加载失败与越权域名分别显示本地化错误与重试，系统返回键先回退网页历史；WebView 关闭文件与内容访问、不注入 JS 桥、混合内容一律拒绝。
- 构建配置新增 WEB_TOOL_BASE_URL（默认 https://tool.zhzgo.cn，debug 覆盖为 http://localhost:8088）与 buildConfig=true；新增网络安全配置，主变体禁用明文，debug 变体仅对 localhost/127.0.0.1/10.0.2.2 放开。
- 离线 assembleDebug/lintDebug 通过（重建 1 分 25 秒），0 错误、24 条既有警告、0 hint；首轮的 2 条 UseKtx 与 1 条 AutoboxingStateCreation 已修正；debug BuildConfig 核对为 http://localhost:8088；APK 17,900,580 字节。
- 打开路径已接线：ToolDetailPage 按 ToolMode.WEB 走 ToolboxWebScreen（标题取工具名、关闭回调复用返回逻辑），其余模式仍走原生 Screen()；离线 assembleDebug/lintDebug 复核通过（1 分 22 秒，0 错误、24 条既有警告、0 hint）。仍缺：/tools/<code>/ 页面与 Nginx 托管、WEB 模式的目录登记、真机 WebView 加载/离线/证书错误/返回键验收。
2026-10-04，官网网页版承载目录（Decision 019 第 3 步之一）：

- 新增 deploy/site/tools/qr/index.html：官网静态根下按 /tools/<code>/ 约定提供二维码网页版占位页（说明网页版承载、后续迁移的裁剪/SVG/JPEG/WPA3/EAP/vCard 能力、数据在本站处理的提示），无外部依赖、适配深色模式与安全区。
- 重建 admin 镜像后容器健康；实测 http://localhost:8088/tools/qr/ 返回 200 且页面包含标题，官网根与 /admin/ 仍为 200，说明新目录与既有路由无冲突。
- 仍缺：把该页登记成 mode=WEB 的目录记录（需要一条新迁移）、页面内部的真实生成功能，以及真机 WebView 加载/离线/证书错误/返回键验收。
2026-10-04，首个 WEB 工具登记（Decision 019 第 3 步之二）：

- 新增 V16__seed_web_qr_studio_tool.sql，登记 qr_studio（二维码工作台、分类 QR、mode=WEB、排序 211、启用不推荐）；原生 qr 工具保持 LOCAL 不变，两者编码不同互不影响。
- 官网目录按编码对齐：deploy/site/tools/qr_studio/index.html；原 /tools/qr/ 占位页已删除，避免编码与目录不一致。
- 实测：Flyway 执行 V16 后 schema 为 v16、历史 16 条全部 success=1；tool_definition 中 qr_studio 为 WEB/ENABLED/211；直连 8081 与代理 8088 的 /api/v1/tools 均返回 total=12 且包含 qr_studio；http://localhost:8088/tools/qr_studio/ 返回 200；server 与 admin 容器健康。
- 仍缺：页面内部的真实生成功能（裁剪、SVG/JPEG、WPA3/EAP、vCard 多值）、真机 WebView 加载与离线/证书错误/返回键验收；客户端目录远程合并与 WEB 工具在 App 内的可见性也尚未接。
2026-10-04，网页版二维码工作台（Decision 019 第 3 步之三）：

- 官网目录新增真实页面：deploy/site/tools/qr_studio = index.html（表单与深色适配）+ payload.js（纯载荷构建，可被 node 测试）+ app.js（渲染与导出）+ qrcode.js（Kazuhiko Arase 的 qrcode-generator 1.4.4，MIT，随站点自带以满足“只从本站加载资源”的白名单约束）。
- 生成能力：文本/链接、Wi-Fi（WPA/WPA2、WPA3、WPA2/WPA3 企业 EAP、WEP、开放网络、隐藏网络、EAP 方法/身份/匿名身份/阶段二）、vCard 3.0 联系人（多电话、多邮箱、多地址，含转义）；输出支持容错等级、512/1024/2048 尺寸、留白 2/4/6/8 模块，并导出 PNG/JPEG/SVG 与复制编码内容。
- 验证：node 执行 payload.js 断言全部通过（文本直通、Wi-Fi 的 WPA 转义、开放网络省略密码、EAP 的 E/I/PH2 字段、vCard 多值与转义、CRLF 分隔）；修复了两处自查发现的问题——载荷文件里的换行转义被写成双反斜杠、vCard ADR 缺少末尾 4 个分号；重建 admin 镜像后容器健康，/tools/qr_studio/ 返回 200（5760 字节、含表单），payload.js/app.js/qrcode.js 均 200。
- 未做：浏览器自动化或真机 WebView 交互验收（表单输入、下载、深色模式），客户端目录远程合并，以及 /tools/qr_studio/ 之外的其它网页版工具。
2026-10-04，App 内注册 WEB 工具（Decision 019 收尾之一）：

- 新增 feature/webtools/QrStudioToolDefinition.kt：code=qr_studio、分类 QR、mode=WEB、sortOrder=211、图标复用 qr、关键词含“网页版/svg/vcard”，并注册进 ToolboxAppContainer 的 ToolRegistry；中文字符串描述与 V16 数据库描述逐字一致，英文另有独立文案。
- 打开路径：HomeRoute 的 ToolDetailPage 已按 mode 分支，WEB 工具由 ToolboxWebScreen 承载（debug 指向 http://localhost:8088/tools/qr_studio/，release 指向 https://tool.zhzgo.cn/tools/qr_studio/），定义里的 Screen() 仅作直接调用时的兜底。
- 效果：二维码工作台现在会出现在 App 的目录/搜索/首页中，点开即用内置 WebView 加载官网页面；本轮之后新增 WEB 工具，只需补一条本地定义（或等目录远程合并落地）。
- 验证：离线 assembleDebug/lintDebug 通过（1 分 18 秒），0 错误、24 条既有警告、0 hint；APK 18,149,704 字节；git diff --check 通过。未做真机点击与 WebView 加载验收。
- 仍缺：目录远程合并（否则每个 WEB 工具都要在客户端补定义）、浏览器/真机交互验收。
2026-10-04，目录远程合并接线（Decision 019 收尾之二）：

- 新增 core/network/ToolCatalogClient.kt：用系统 HttpURLConnection + org.json（不引入 HTTP/序列化依赖）分页请求 /tools，只挑出 mode=WEB 的条目；含 5s/8s 超时、非 2xx 报错、响应体积上限 100 万字符。
- 构建配置新增 API_BASE_URL（默认 https://toolapi.zhzgo.cn/api/v1，debug 覆盖为 http://10.0.2.2:8081/api/v1 以适配模拟器回环；真机需改成局域网地址，明文仅对 debug 白名单放开）。
- ToolboxAppContainer 增加一次进程内后台刷新：拉取成功后把 WEB 条目经 ToolCatalogDto.toMetadataOrNull() 校验、构造通用 WebToolDefinition，再调用 registry.withExtra(...) 与 repository.updateRegistry(...)；失败保持内置注册中心。首页在下一次刷新时看到新工具。
- 验证：离线 assembleDebug/lintDebug 通过（1 分 32 秒），0 错误、24 条既有警告、0 hint，新增文件无 Lint 问题。
- 未做：真机上实际请求与解析、刷新时机与被动的界面更新（目前需一次刷新）、响应缓存（失败仅回退内置注册）、浏览器自动化与真机 WebView 交互验收。
## 已有实现范围

2026-10-04，远程 WEB 目录自动更新：

- 修复只有首页内部引用更新且需手动刷新、搜索/收藏/最近使用始终持有旧注册中心的问题。新增共享 ToolRegistryStore，首页和搜索观察快照，收藏与最近使用合并 DAO/目录 Flow 后重新解析元数据。
- 保留标签、查询、分类及权重；订阅前到达的结果不会丢失，后续成功替换会移除旧纯远程定义；内置定义优先，失败不替换目录，非 WEB 定义拒绝整次发布。
- 12/12 持久化 JUnit 用例通过（0 失败、0 错误、0 跳过），Android testDebugUnitTest/assembleDebug/lintDebug 成功（7 分 22 秒）；Lint 为 0 错误、25 条警告，其中新增 1 条为与现有协程同版本的测试库新版本提示。actionlint 校验通过，CI 已加入单元测试及报告上传。
- 开源参考、边界与测试范围见 docs/TOOL_CATALOG_SYNC.md。没有执行真实 HTTP/SQLite/设备/WebView 或托管 CI；持久缓存、网络读取/分页预算与重复刷新策略仍待后续任务。

2026-10-05，远程目录网络契约及预算修复：

- 读取时执行单页/累计字节限制，严格 UTF-8 与有界 JSON，禁止类型转换、重复字段、异常业务封装/trace、无效元数据；各页总数、页码、数量、编码唯一性和顺序全部校验后再过滤 WEB，拒绝超页数而不发布部分目录。
- 新增跨页 20 秒总期限，保留 5 秒连接/8 秒单次读取超时；取消/超时后等待方结束、过期结果丢弃，连接由 IO 线程尽力断开。RefreshWebToolCatalogUseCase 统一完整成功发布与失败保留旧快照。
- 39 个新增持久化测试和原 12 个观察测试共 51/51 通过（0 失败/错误/跳过），其中使用真实 JBR loopback HTTP 验证固定长度、分块和读取超时；离线 testDebugUnitTest/assembleDebug/lintDebug 成功（2 分 22 秒），Lint 0 错误、22 警告，目录源码/测试无新增问题；git diff --check 通过。没有业务/测试依赖升级，也不把缓存的版本提示数量变化归因为修复。
- 源码参考、功能边界和实际验证见 docs/TOOL_CATALOG_SYNC.md。本轮未修改 API/数据库/权限；API 无跨页快照版本，底层 DNS/不可中断 I/O 清理不保证立即完成，实际服务、Android 提供者、SQLite、设备/WebView、托管 CI 未验收；持久缓存与重复刷新策略仍待后续任务。

| 工具 | 接入情况 | 当前限制 |
| --- | --- | --- |
| 单位转换 | Registry、页面、分层代码、V4 迁移 | 默认交换及大指数处理有缺陷；实际 12 类、88 个单位 |
| 时间戳 | Registry、页面、分层代码、V5 迁移 | 无效日期会被自动改为有效日期 |
| UUID | Registry、页面、V6 迁移 | ViewModel 绕过已注入的 UseCase |
| Base64 | Registry、页面、分层代码、V7 迁移 | 非 UTF-8 字节会被静默替换，需明确文本解码策略 |
| URL 编解码 | Registry、页面、分层代码、V8 迁移 | 加号往返丢失；组件编码与文档所称 RFC 3986 不一致 |
| 哈希 | Registry、页面、分层代码、V9 迁移 | 一个 MD5 已知样例通过，未覆盖全部算法和界面 |
| JSON | Registry、页面、分层代码、V10 迁移 | 宽松解析及根字符串输出错误 |
| 文本处理 | Registry、页面、分层代码、V11 迁移 | 用户正则在主线程执行，存在卡死风险 |
| 二维码 | Registry、页面、分层代码、V12 迁移 | 图片识别、Wi-Fi 转义、多语言及原规格功能范围仍有缺口 |
| 图片压缩 | Registry、页面、分层代码、V13 迁移、FileProvider | 大图、选择竞争、预览、旧系统保存及加载样式存在问题 |

“已接入”仅说明代码存在并能构建，不表示所有场景已验收。V4–V13 文件已被打包，并于 2026-10-04 在本机容器中实际执行成功（见修复进度）。

## 高优先级问题

### 1. 单位转换的大指数输入可能触发巨大字符串分配

- 位置：`android/app/src/main/java/com/cangshuo/toolbox/feature/converter/ui/ConverterViewModel.kt:101`、`:138`。
- `BigDecimal` 接受科学计数法。64 字符的输入长度限制不能限制指数，例如 `1e100000000`。
- 代码先调用 `toPlainString()`，再检查输出是否超过 128 字符，内存分配发生在限制之前；转换和格式化也在 ViewModel 的主线程状态流中执行。
- 超长结果被截断并附加省略号，复制操作会复制截断后的文本，不能作为完整数值使用。
- 建议限制指数、精度和结果规模，在展开字符串之前检查；显示格式与可复制的完整数值分开处理。此风险由代码确认，本次未执行可能耗尽内存的输入。

### 2. 图片压缩默认原尺寸解码，没有像素内存预算

- 位置：`android/app/src/main/java/com/cangshuo/toolbox/feature/imagecompress/data/LocalImageCompressRepository.kt:150`、`:176`、`:184`。
- `ORIGINAL` 模式的目标尺寸等于原图尺寸，采样值为 1；大图按 ARGB_8888 完整解码，随后可能同时存在旋转图、缩放图、输出缓冲区和输出字节数组。
- 一亿像素仅像素数据就约 400 MB。预览缩略图并不能保护实际压缩路径；`runCatching` 也不能代替内存预算。
- 建议增加最大像素/内存限制、明确超限反馈，并减少同时持有的 Bitmap 与字节副本。设备内存压力场景尚未运行。

### 3. 二维码读图与识别同步占用主线程

- 位置：`android/app/src/main/java/com/cangshuo/toolbox/feature/qr/ui/QrScreen.kt:79`、`android/app/src/main/java/com/cangshuo/toolbox/feature/qr/ui/QrViewModel.kt:167`。
- 图片选择回调直接打开流并无采样解码，随后分配 `IntArray(width * height)` 并同步识别。大照片会同时占用 Bitmap 和像素数组内存，并可能造成界面长时间无响应。
- UI 直接处理文件流也不符合项目 UI → ViewModel → UseCase → Repository 的边界。
- 建议将读取、采样和识别移入 Repository 的后台任务，增加尺寸预算、取消及失败状态。设备场景尚未运行。

### 4. 文本正则可能让界面无响应

- 位置：`android/app/src/main/java/com/cangshuo/toolbox/feature/text/data/LocalTextRepository.kt:81`、`android/app/src/main/java/com/cangshuo/toolbox/feature/text/ui/TextViewModel.kt:67`。
- 用户提供的正则通过 `toRegex().replace()` 执行，调用发生在主线程的状态流中，没有执行时间预算。
- 某些回溯表达式（例如 `(a+)+$` 配合很长的近似匹配文本）可能消耗大量时间；输入长度上限不能保证完成时间。
- 建议限制正则能力或采用有执行复杂度保证的实现，并增加后台执行、任务隔离和取消策略。仅切换线程或加协程超时不能确保停止正在执行的同步正则。本次未运行可能卡死的样例。

### 5. JSON 格式化会改变合法根字符串，校验并非严格 JSON

- 位置：`android/app/src/main/java/com/cangshuo/toolbox/feature/json/data/LocalJsonRepository.kt:18`、`:25`、`:47`。
- 根字符串输入 `"abc"` 经 `nextValue()` 得到普通字符串后，通过 `toString()` 输出为 `abc`，丢失 JSON 字符串的引号。
- Android `JSONTokener` 是宽松解析器，允许注释、单引号及其他非标准语法。成功解析不能作为严格 JSON 合法性的证明；检查尾部字符不能解决这些差异。[Android 官方 JSONTokener 文档](https://developer.android.com/reference/org/json/JSONTokener)
- 建议使用严格解析和正确的 JSON 值序列化，明确数字精度与嵌套深度策略。上述判断来自代码与官方语义，本次未在 Android 运行时执行 JSON 样例。

## 结果与兼容性问题

### 6. URL 编解码不能保持加号往返

- 位置：`android/app/src/main/java/com/cangshuo/toolbox/feature/urlcodec/data/LocalUrlCodecRepository.kt:36`、`:45`。
- 已执行样例：`https://example.com/?q=a+b` 经完整 URL 编码再解码，变为 `https://example.com/?q=a b`。编码保留 `+`，解码采用表单解码语义把它变成空格。
- 已执行样例：组件编码 `a!()*'` 返回原文，与文档承诺的 RFC 3986 非保留字符规则不一致。
- 建议明确区分 URI 百分号解码和表单解码，统一模式、实现与文档。

### 7. 不存在的日期会被悄悄改写

- 位置：`android/app/src/main/java/com/cangshuo/toolbox/feature/timestamp/data/LocalTimestampRepository.kt:45`、`:55`。
- 已执行样例：输入 `2026-02-30 12:00:00`，返回 `2026-02-28 12:00:00`。
- 原因是格式解析器采用默认 SMART 解析。建议使用适合严格解析的年份模式与 `ResolverStyle.STRICT`，对不存在的日期返回错误。

### 8. 默认单位点击交换没有效果

- 位置：`android/app/src/main/java/com/cangshuo/toolbox/feature/converter/ui/ConverterViewModel.kt:63`。
- 默认实际单位由 `buildState()` 根据空 ID 推导；`swap()` 却交换 SavedStateHandle 内的空 ID。初始页面或切换分类后，默认单位仍会再次按原顺序推导。
- 建议交换当前已解析的单位 ID。此项为静态代码检查，尚未执行设备点击验收。

### 9. 快速选择图片或清空时可能被旧任务覆盖

- 位置：`android/app/src/main/java/com/cangshuo/toolbox/feature/imagecompress/ui/ImageCompressViewModel.kt:31`。
- 每次选择都会创建未跟踪的元数据读取任务；旧任务晚于新任务完成时，仍会写入旧预览并启动旧图片压缩。清空只取消压缩任务，不能阻止尚未完成的读取任务重新填充状态。
- 建议取消/跟踪整个选图流水线，并以请求标识或当前 URI 校验结果归属。设备竞争场景尚未运行。

### 10. 小图的“压缩后”预览仍使用压缩前 Bitmap

- 位置：`android/app/src/main/java/com/cangshuo/toolbox/feature/imagecompress/data/LocalImageCompressRepository.kt:219`。
- 当缩放后宽高均不超过 1024 时，预览直接使用 `scaledBitmap`，没有从实际压缩字节解码，无法反映 JPEG/WebP 的质量损失。
- 建议所有压缩后预览均来自输出字节，并采用采样读取。

### 11. Android 8/9 的保存到相册缺少权限处理

- 位置：`android/app/src/main/java/com/cangshuo/toolbox/feature/imagecompress/data/LocalImageCompressRepository.kt:282`、`android/app/src/main/AndroidManifest.xml`。
- 项目最低支持 API 26；API 26–28 路径直接写外部 MediaStore，却没有对应存储权限声明或申请。
- 官方文档要求旧系统写共享媒体时处理存储权限，API 29+ 的自有媒体免权限规则不能覆盖旧系统。[Android 官方媒体存储文档](https://developer.android.com/training/data-storage/shared/media)
- 建议为旧系统使用合适的保存交互或限定版本的权限流程，并同步修改“无需权限”的文档描述。旧系统设备尚未验证。

### 12. Wi-Fi 二维码没有转义 SSID 和密码

- 位置：`android/app/src/main/java/com/cangshuo/toolbox/feature/qr/ui/QrViewModel.kt:193`。
- SSID 和密码直接插入 `WIFI:` 格式。包含分号、反斜杠等字符时，扫描器可能解析成不同字段，导致连接失败。
- 建议按格式要求转义字段并覆盖特殊字符。[ZXing Wi-Fi 内容格式](https://github.com/zxing/zxing/wiki/Barcode-Contents)

## 一致性与交付缺口

- **加载效果未统一**：图片压缩页面在 `ImageCompressScreen.kt:273`、`:332`、`:467`、`:477` 直接使用不同参数的 `CircularProgressIndicator`，尚未按 UI 规范复用共享加载组件。
- **英语界面不完整**：二维码颜色标签来自 `QrColorStyle.displayName` 的中文硬编码（`QrScreen.kt:285`），部分错误/分享文案直接使用英文。中英资源 key 集合相同不等于所有显示文本都完成本地化。
- **Lint 警告**：英语 `strings.xml:441` 的 `100% offline` 未关闭格式化，触发 `StringFormatCount`；应作为字符串格式问题修复。其余警告仍需逐项分类，不能称为“零警告”。
- **UUID 分层未真正贯通**：`UuidViewModel.kt:33` 直接调用 `UUID.randomUUID()`，已注入的 `GenerateUuidUseCase` 未使用；文档所示分层链路与页面执行路径不一致。
- **Base64 非 UTF-8 策略**：已执行 `/w==` 解码，返回成功及替换字符 `U+FFFD`。该输入是合法 Base64 字节，但不是合法 UTF-8 文本；需明确是严格报错、显示字节还是允许替换，避免用户误以为文本完整还原。
- **路线图不准确**：Phase 2 勾选了 9 个工具，却遗漏原有二维码任务条目；二维码的导出/分享等原规格要求未全部实现。应恢复条目并按实际完成范围标记。
- **单位数量不准确**：`ROADMAP.md` 与 `UNIT_CONVERTER.md` 写 62 个单位，实际代码枚举结果为 88 个。
- **交接资料过时**：`AI_HANDOFF.md` 仍指向旧提交及“下一步单位转换”，未反映当前未提交工具、验证范围及缺陷；`PROJECT_SPEC.md` 的进度描述也需同步。
- **自动测试缺口**：仓库未发现新增工具的项目测试源码。本次检查用样例不等于已经建立持续回归门禁。

## 本次实际验证

| 检查 | 结果 | 能证明的范围 |
| --- | --- | --- |
| Android 离线 `:app:assembleDebug :app:lintDebug` | BUILD SUCCESSFUL；0 errors、32 warnings | 当前环境可编译并通过 Lint 错误门禁，不代表所有功能正确 |
| Server 离线 Maven package | BUILD SUCCESS；测试明确跳过 | 当前源码和迁移资源可打包，不代表迁移已执行 |
| 已编译业务类的 12 个样例检查 | 8 项通过，4 项未满足检查预期 | Base64、URL、时间戳、MD5、UUID、单位转换及自然排序的有限样例 |
| `git diff --check` | 通过 | 已跟踪文件差异无空白错误 |
| 数据库 / API 联调 | 2026-10-04 已执行（见修复进度） | Docker Linux 引擎启动、重建 server 镜像后 Flyway V4–V13 全部 `success=1`、schema v13；GET /tools total=11、DEV=6、分页与 `pageSize=0` 校验、Nginx 与健康检查均符合预期 |
| 真机 / 模拟器 / 英语切换 | 未执行 | 大图、交互、旧系统保存和完整语言显示仍待验收 |

样例通过项：Base64 标准编码与往返、UTC epoch、MD5 已知向量、100 个 UUID v4 的合法性/唯一性、100℃ → 212℉、自然数字排序。未满足预期项：URL RFC 3986 模式、URL 加号往返、无效日期拒绝、Base64 非 UTF-8 不静默替换。其中 Base64 项需要先明确产品策略。

验证产物位于 D 盘项目目录：`android/app/build/outputs/apk/debug/app-debug.apk`、`android/app/build/reports/lint-results-debug.html` 及 `server/target/`。本次检查脚本在完成检查后清理；没有删除其他任务的临时文件或缓存。

## 建议下一步顺序

1. 先分别修复单位转换、图片压缩、二维码读图、文本正则的内存和主线程问题，每次控制在一个工具范围。
2. 修复 JSON、URL、日期等会改变用户数据的行为，并为已复现样例建立项目回归测试。
3. 修复图片任务竞争、压缩预览、旧系统保存、Wi-Fi 转义以及共享加载/多语言问题。
4. 同步规格、路线图和交接文档，启动具备依赖的环境后验证数据库迁移与工具目录 API，再进行设备验收和分批提交。

完成这些收尾之前，不建议将本批 Phase 2 标记为全部完成并直接进入下一阶段。
