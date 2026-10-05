# AI 接手说明（新窗口 / 换模型）

本文件给新的 AI 会话使用：新开窗口或切换模型时，把下面「开场消息」整段发给 AI，让它从仓库读取现状，不依赖人复述。

## 最新状态（2026-10-05，优先于下方历史记录）

用户明确要求停止真机验收，直接进入下一步；不要继续等手机或重复征求确认。Phase 2 功能范围完成，设备/第三方应用验证延后到发布，仍保持待验证记录。此前手机 stay_on_while_plugged_in 从 0 临时改成 7；未来手机重新连接时恢复为 0，本轮未连接/操作手机。

Phase 3 功能已完成：注册登录/JWT、V19 哈希刷新会话与 Keystore/DataStore 持久恢复、PUT /auth/me 昵称编辑、V20 收藏/最近/设置同步。Android Room v7 与 4→5→6→7 保留数据迁移，独立默认关闭开关和账号分区、LWW tombstone/outbox/游标；主题、语言、列数、启动页已接实际 UI。最终 Server 40、Android 170、实际认证 API 35 / 同步 API 37、主机 SQLite 30 项通过，Android test/assemble/lint 成功（最终 1 分 24 秒），Lint 0 错误/28 警告。本地服务 schema v20，目录仍 12 项。客户端同账号续期保持身份，401 me 立即清账号，避免刷新取消自身同步；匿名设置不上传，隐私开关不云同步。资料邮箱改动/头像上传、密码重置、设备会话列表、系统语言选择器双向桥接未实现；设备/Keystore/两台 Android 验收按用户要求延期，不能声称真机验收完成。具体边界 docs/AUTH.md / docs/CLOUD_SYNC.md。

用户明确要求没有重要阻塞不要停，普通路线图任务完成后继续下一项，不要发送 final 后等“继续”。Phase 4 Device & Network 八项全部完成：设备信息(V21)、存储(V22)、电池(V23)、传感器(V24)、指南针(V25)、水平仪(V26)、Ping(V27)、HTTP 状态(V28)；最终 186 项 Android 测试、assembleDebug、lintDebug（0 错误/29 警告，含 OkHttp 3.14.9 版本提示）通过，本地目录 20 项。Phase 5 进行中：Admin auth 完成（V29、独立管理员 JWT、限流、独立事务审计、引导变量、后台登录页/守卫/退出），Tool CRUD 完成（管理端列表/详情/新增/编辑/上下线与维护/软删除、只读分类选项、公开目录联动、tool 审计；无数据库结构变更），Category CRUD 完成（分类列表/新增/编辑/启停/软删除、在用保护 409/30007、停用分类隐藏工具、category 审计；无数据库结构变更），Recommendation slots 完成（V30 home_recommendation：工具或 https 链接目标、排序、启停、UTC 生效时间窗；管理端 CRUD + 审计与匿名 GET /api/v1/home/recommendations 仅返回生效项；Android 首页接入未做、不得声称 App 已生效），User management 完成（搜索/详情/收藏最近计数/掩码会话列表/同步键列表，停用即撤销全部刷新会话并让访问令牌立刻失效，user 审计；无数据库结构变更），Announcements 完成（V31 announcement：级别/草稿与发布/UTC 时间窗；管理端 CRUD + 审计与匿名 GET /api/v1/home/announcements 最多 5 条纯文本），Feedback 完成（V32 user_feedback：用户提交与 /feedback/my、管理员关键词/状态检索、处理与回复、feedback 审计；Android 端接入未做），Operation logs 完成（只读 GET /api/v1/admin/logs：模块/结果/关键字过滤、分页、管理员名外连接）。Phase 5 全部八项完成（Admin auth、Tool CRUD、Category CRUD、Recommendation slots、User management、Announcements、Feedback、Operation logs）。Phase 6 已开始：MinIO 完成（MinIO SDK 8.5.17、默认关闭的存储开关、懒建桶、有界上传+服务端 SHA-256、预签名下载、删除、仅在启用时注册的健康指标、管理员 /api/v1/admin/storage 接口与 storage 审计；无数据库结构变更）。当前验证基线：Server 99/99 测试、admin typecheck/build、管理员端 20/20+20/20+22/22+26/26+19/19+25/25+17/17+13/13、对象存储 23/23（真实本地 MinIO 容器，含网络内预签名下载哈希一致）。File security 已完成（上传路径 32 字节魔术字节嗅探 + 声明/检测 MIME 严格一致，拒绝二进制伪文本与主动内容类型），Quotas 已完成（V33 stored_object 记账、每所有者字节/对象数与全局字节三道写入前校验 413/40004、管理端状态暴露用量、删除释放额度；Server 107/107 测试、真实 MinIO 上 38/38 存储校验）。Rate limits 已完成（Redis 固定窗口 + 内存兜底、身份分桶、登录端点更严格、统一 429/10007 与 X-RateLimit/Retry-After 头；Server 116/116 测试与 61/61 限流校验，随后全量回归 13/20/22/26/19/25/17/38/20 全部通过，环境已恢复默认并清空临时数据）。Phase 6 中不依赖外部服务的部分全部完成；OCR、PDF、AI 需要外部服务商与凭据决策（已向用户提问，等待选择）；Web tool release acceptance 仍在待验收清单。手机 stay_on_while_plugged_in=7 的恢复仍待手机重新连接时执行（当前未连接）。混合工作区未提交/推送，不能擅自全量提交；临时目录 .tmp/phase3 待最终无用文件清理。所有可控文件保存在 D 盘。

## 开场消息（复制粘贴）

请接手「沧烁工具箱」项目开发。

- 项目目录：D:\codeStudy\code\cangshuo-tool（远端 https://github.com/zhz520/cangshuo-tool，主分支 main）
- 先按顺序阅读：
  1. toolbox-vibe-spec/AGENTS.md（开发约定）
  2. toolbox-vibe-spec/ROADMAP.md（进度与下一步）
  3. toolbox-vibe-spec/PROJECT_SPEC.md（完整设计）
  4. 涉及 Android UI 时再读 docs/ANDROID_UI_SPEC.md，视觉参考 stitch_cangshuo_tool_android_ui_redesign/
  5. 开发或修复工具时读 docs/TOOL_DEVELOPMENT_GUIDE.md，阅读 GitHub 相关优秀源码，并在工具文档记录实际参考、适配与功能/边界覆盖。
- 先检查 Git 工作区，并阅读 docs/DEVELOPMENT_REVIEW_2026-10-04.md 的修复进度；优先完成尚未解决的审查问题，再从 ROADMAP 中第一个未完成任务开始。每次只做一个任务，先说明最小计划和文件范围，再实施。
- 完成后执行适用的构建/Lint，更新 ROADMAP 与对应 docs，提交信息使用 feat:/fix:/refactor:/test:/chore: 前缀。
- 新建文件保存到 D 盘：项目文件放项目目录，独立交付物放 D:\CodexData\outputs，临时文件放 D:\CodexData\temp。
- 用中文交流。

当前进度（2026-10-04 工作区快照；先以本机 Git 和文件为准）：
- Phase 1 已完成：首页、工具注册、Server GET /tools、计算器、本地搜索、本地收藏、本地最近使用；设备运行验证仍待做。
- Phase 2 新增单位转换、时间戳、UUID、Base64、URL 编解码、Hash、JSON、文本工具、二维码、图片压缩，共 10 个工具；加上计算器共注册 11 个。新增代码、文档和 V4–V14 迁移仍未提交，远端旧提交不能代表本机工作区。
- 已完成代码审查并写入 docs/DEVELOPMENT_REVIEW_2026-10-04.md。单位转换的默认交换、数值规模限制、完整科学计数法及单位标签已完成代码修复；最终 Android 构建/Lint 成功，0 错误、32 警告；未新增或运行自动化测试，设备与回归验收仍待做。细节见该工具文档和报告修复进度。
- 图片压缩已按实际 GitHub Compressor 源码参考完成代码修复：大图预算、文件输出、旧任务归属、真实预览、API 26–28 系统文件保存、后台分享及共享加载/中英反馈；最终离线 Android 构建/Lint 成功（65 秒），0 错误、27 警告；设备、导出与自动化回归验证仍待做。细节见 docs/IMAGE_COMPRESS.md。
- 用户要求已写入两处 AGENTS 和 docs/TOOL_DEVELOPMENT_GUIDE.md：以后每个工具开发/修复要阅读相关 GitHub 优秀源码，功能与边界完整，并写明来源、采用思路和实际验证，不能只列项目名称。
- 二维码已按 ZXing 3.5.3 与 JourneyApps 实际源码完成本轮代码修复：后台 URI 读图/采样/识别、内存和读取预算、取消与旧任务归属、反色重试、Wi-Fi 字段转义/隐藏网络、四模块留白、共享加载和中英反馈。最终离线 Android 构建/Lint 成功（54 秒），0 错误、25 警告；临时目录已清理，未运行自动化/样例/设备验收，见 docs/QR_TOOL.md。结果导出随后补齐（见下），相机/条码/专用类型仍未完成，路线图不勾选。
- 文本工具已按 RE2/J 1.8 与 IT-Tools 实际源码完成本轮代码修复：后台串行处理、编译/输出预算、取消/版本归属、分组替换与选项、Unicode/换行统计、长数字排序、统一加载和中英反馈。最终离线 Android 构建/Lint 成功（58 秒），0 错误、27 警告；文本源码/新增资源无 Lint 问题，75 个中英 key 对齐，许可证已打包，临时目录已清理；未运行样例、自动化或设备验收，语法子集和协作取消边界见 docs/TEXT_TOOLS.md。
- JSON 已完成本轮代码修复：参考实际 CyberChef/IT-Tools 与 Lottie reader 源码及 RFC，使用有界严格扫描，保留根字符串引号、数字原文和全部重复成员；补齐严格转义/两种反转义、后台处理、取消/版本、资源预算、错误位置、统一加载和中英提示。三轮离线 Android 构建/Lint 均成功，最终 55 秒、0 错误、25 警告；JSON 源码/相关资源无 Lint 问题，54 个中英 key 对齐，本轮临时目录已清理；未运行业务样例、自动化或设备验收，Unicode 额外限制与其余边界见 docs/JSON_TOOL.md。
- URL 编解码已完成本轮代码修复：参考 WHATWG URL Standard 与实际 jsdom/whatwg-url、CyberChef、IT-Tools 源码，移除 `java.net.URLEncoder/URLDecoder` 表单语义混用，改为组件/URI 整体/表单值三类型的严格单层百分号编解码；修复加号往返与组件字符集，加入严格 %HH/UTF-8 校验、错误位置、后台互斥、取消/版本、预算、预览截断提示、共享加载和中英反馈。三轮离线构建/Lint 成功，最终完整构建 83 秒、增量 15 秒，0 错误、24 条警告（本轮开始 25 条），44 个中英 key 对齐，临时目录已清理；V8 不变，未运行自动化/样例/设备验收，见 docs/URL_CODEC.md。
- 时间戳已完成严格日期解析修复：参考实际 IT-Tools/CyberChef 与 OpenJDK ResolverStyle/DateTimeFormatter 源码，改用 `ResolverStyle.STRICT` + `uuuu` 模式，`2026-02-30` 等不存在日期返回错误；新增 InvalidDate 分类与中英提示、1900–3000 年份范围、夏令时缺口拒绝与重叠策略。JBR 验证了严格/宽松行为、闰年和 DST；离线构建/Lint 成功（1 分 26 秒），0 错误、24 警告，30 个中英 key 对齐，临时目录已清理；未运行自动化/设备验收，见 docs/TIMESTAMP.md。
- UUID 分层已修复：移除 ViewModel 直接调用 `UUID.randomUUID()` 与字符串拼接，生成/格式化统一经 GenerateUuidUseCase → UuidRepository（generateRaw/format），初始状态按 SavedState 选项格式化。离线构建/Lint 成功（1 分 16 秒），0 错误、24 警告，16 个中英 key 对齐；JBR 验证 10,000 个 v4 UUID 全部合法且无重复，临时目录已清理；未运行自动化/设备验收，见 docs/UUID_GENERATOR.md。
- Base64 非 UTF-8 策略已修复：解码字节优先、文本严格 UTF-8；非法字节不再替换为 U+FFFD，显示无效字节数与首个位置，禁用文本复制/互换；解码后始终提供无损 Hex 视图（预览 8000 字符、复制完整）。参考 CyberChef/IT-Tools 实际源码，离线构建/Lint 成功（1 分 29 秒，文案重跑 1 分 40 秒，最终增量 1 分 10 秒），0 错误、24 警告，25 个中英 key 对齐；JBR 对最终编译类复验 20/20 通过，临时目录已清理；未运行自动化/设备验收，见 docs/BASE64_CODEC.md。
- 二维码多语言收尾已完成：WPA/WPA2、WEP 与 L/M/Q/H 标签移入中英资源（协议原文、两端同值），领域枚举不再携带展示字符串；QR feature 未再发现用户可见硬编码字面量，`qr_*` 中英各 58 个 key 对齐；离线构建/Lint 成功（1 分 22 秒），0 错误、24 警告。此前 QrColorStyle.displayName 与英文分享文案问题在当前代码中已不存在。
- 页面切换动画已统一：`core/ui/ToolboxMotion.kt` 提供工具/搜索前进后退（进入 240ms、退出 180ms 的滑动+淡化）与底部标签 180ms 交叉淡化；HomeRoute/HomeScreen 已接入，页面不再自定义动画；显式加入 Compose Animation 依赖（BOM 管理）。离线构建/Lint 成功（2 分 48 秒，全量），0 错误、24 警告；关闭系统动画、TalkBack、快速连续切换与低端设备验收仍待执行，见 docs/ANDROID_UI_SPEC.md。
- 页面特性宣传语已移除：11 个工具页的“本地处理/无需登录或联网”标签、首页“本地优先”标语、卡片运行模式徽标全部删除；搜索/收藏/我的/二维码说明改为中性功能描述；工具描述去掉“本地计算/离线处理/保护隐私”等收尾措辞，URL 工具加载/错误文案改为中性。`ToolMode` 数据保留但不再在界面展示。Android UI 规范已新增“禁止特性宣传语”规则；离线构建/Lint 成功（完整 2 分钟，最终增量 1 分 3 秒），0 错误、24 警告，无残留引用；服务端描述已通过下述 V14 统一；设备/语言验收仍待执行，见 docs/ANDROID_UI_SPEC.md。
- 服务端默认描述已对齐：新增 V14，按 code 和二进制精确原始种子值替换 11 条描述，保留人工编辑及其他目录配置；V1–V13 未改。Server 镜像构建成功（Maven 3.448 秒、测试跳过），Flyway 实际执行 V14（27ms），schema v14、14 条历史全部 success=1、旧校验值不变。会话临时表验证自定义/尾空格保留和关闭/软删除配置不变；直连/代理目录一致，11 条说明与 Android 中文资源相同，其他公开字段未变，五个容器健康；本轮临时文件清理，未新增持久化自动测试或执行 Android/设备验收。见 docs/TOOL_CATALOG.md 和 docs/DATABASE.md。
- 二维码结果导出已补齐：阅读 ZXing 3.5.3 QRCodeEncoder/EncodeActivity 实际源码，新增分层导出 Repository/UseCase、512/1024/2048 PNG、整数网格和留白、API 29+ 相册/API 26–28 系统文件保存、FileProvider 分享、识别原文分享与重新生成；导出快照/互斥、预算/失败清理、共享加载与双语反馈。两轮离线构建/Lint 成功（93 秒、最终 81 秒），0 错误、24 既有警告，79 个中英 QR 字符串 key 对齐；JBR 编译布局 120 例和 Java2D PNG/ZXing 144 次往返通过，不覆盖 Android Canvas/相册/SAF/分享接收方，设备验收仍待做，未新增持久化自动测试；临时脚本清理。见 docs/QR_TOOL.md。
- 二维码相机扫描与条码支持已补齐：引入 CameraX 1.6.2、按需 CAMERA 权限（工具打开不拦截）、无相机/权限被拒/启动失败三态、生命周期绑定、手电筒、Y 平面去 stride、90°/270° 转正与半转复用、KEEP_ONLY_LATEST 背压；相机与相册共用 13 种二维码/条码格式（相册解析由 QRCodeReader 改为 MultiFormatReader）。离线构建/Lint 成功（全量 2 分 20 秒、确认 1 分 32 秒），0 错误、24 既有警告、0 hint，93 个中英 QR key 对齐且引用齐全，二维码源码无 Lint 问题；JBR 验证 2 个 Y 平面样例、6 个方向样例、8 次条码往返、13 种格式清单通过，不覆盖 Android ImageProxy/CameraX 绑定/权限对话框/预览/手电筒与真机识别率；APK 增至 17,359,669 字节（CameraX 原生库约 +4.6 MB）。见 docs/QR_TOOL.md。
- 二维码专用内容类型已补齐：生成侧现有 7 类内容（文本/网址、Wi-Fi、电话、邮箱、短信、联系人、日历事件）。电话/短信使用 `tel:`/`SMSTO:`，邮箱使用 `mailto:`，联系人使用 MECARD（按 ZXing 3.5.3 `MECARDContactEncoder` 的字段顺序与转义规则，参考同时阅读了 `ContactEncoder`/`VCardContactEncoder`），事件使用 iCalendar VEVENT 本地浮动时间；日期按 `uuuu-MM-dd`、时间按 `HH:mm` STRICT 解析，结束时间可留空但必须晚于开始。字段上限 256、短信正文 500、日期 10、时间 5，全部通过 `QrInputPolicy` 校验与 `hasContent` 空状态判断，表单值写入 SavedState。离线构建/Lint 成功（首轮修复 1 条 ModifierParameter 与 2 条 TypographyDashes，最终 1 分 32 秒），0 错误、24 既有警告、0 hint，129 个中英 QR key 对齐；JBR 验证 43 项（7 种类型、格式拒绝、MECARD/iCalendar 转义、STRICT 日期时间、结束早于开始、真实 QR 往返）全部通过，未覆盖真机与第三方扫码器导入行为。见 docs/QR_TOOL.md。
- 二维码多码识别（相册）已补齐：解析页新增“识别多个二维码”开关（SavedState 恢复），开启后由 `QrMultiDecoder` 调用 ZXing `QRCodeMultiReader` 检测同图全部二维码，按检测顺序去重；1 个结果仍走原结果卡，2 个及以上展示列表并支持逐条复制、复制全部与分享全部（换行连接）；多码未命中时回退单码路径，所以图里只有条码时仍可识别。多码只覆盖二维码，相机仍只取第一个结果。离线构建/Lint 成功（1 分 19 秒），0 错误、24 既有警告、0 hint，135 个中英 QR key 对齐；JBR 验证 9 项（三码、单码、去重、空白、纯条码回退、混合图只取二维码、共享提示、极小帧）全部通过；未覆盖真机耗时与布局。见 docs/QR_TOOL.md。
- 相机多码收集已补齐：多码开关打开时 `QrCameraAnalyzer` 走 collect 分支，用 `QrCameraCollector`（去重、单次上限 50）持续回调新载荷；ViewModel 用内存态 `cameraResults` 保存会话，预览显示已收集数量，按钮文案改为“完成”，停止时 1 个结果进原结果卡、2 个及以上进结果列表，空会话只关闭预览并保留旧结果。扫描期间多码开关禁用，切换开关会兜底结束会话。离线构建/Lint 成功（1 分 16 秒），0 错误、24 既有警告、0 hint，138 个中英 QR key 对齐；JBR 验证 72 项收集器用例（去重、空载荷、自定义与默认上限、Unicode 与协议载荷）全部通过；未覆盖真机收集节奏与布局。见 docs/QR_TOOL.md。
- 识别结果符号名称已补齐：新增 `QrSymbology` 与 `QrDecodeEntry(text, symbology)`，`QrBarcodeFormats.symbologyOf` 统一映射 ZXing `barcodeFormat`（13 种格式 + `UNKNOWN` 兜底）；相机分析器、单码与多码路径都返回条目，UiState 改为 `decodeEntry`/`decodeEntries`，相机会话同样保存条目；结果卡与多码列表显示格式标签，协议名中英同值、仅“未知格式”翻译。离线构建/Lint 成功（1 分 21 秒），0 错误、24 既有警告、0 hint，152 个中英 QR key 对齐；JBR 验证 27 项（13 种映射、null/MAXICODE/RSS_14 回退、QR/Code 128/Code 39/EAN-13 真实解码标注、多码路径）全部通过；未覆盖真机布局与真实样本核对。见 docs/QR_TOOL.md。
- Phase 2 剩余收尾：V4–V13 数据库迁移与 GET /tools 运行时联调已于 2026-10-04 完成；随后 V14 完成默认描述统一（当前 schema v14、14 条历史全部 `success=1`）。二维码相机、13 种符号体系、专用内容类型、相册多码、相机收集、符号名称展示、邮件头部字段与事件纪要已补齐（mailto 支持 RFC 6068 subject/body 百分号编码，VEVENT 支持多行 DESCRIPTION）；线性条码分带多码检测已补齐（QrBandDecoder，三条重叠横向分带，QR 多码未命中时启用，成本接近一次全图解码）；竖向分带与混合多码已补齐（竖带先转置；QR 多码与分带结果按文本合并）；WEB 模式契约层已落地（ToolMode/DTO/Server 允许值/V15 CHECK，Docker 重建 server 镜像后 Flyway 实际执行 V15（00:00.133s），schema 升到 v15、15 条历史全部 success=1，CHECK 约束已含 WEB，目录仍为 11 个工具、0 个 WEB）；WebView 容器与域名白名单已实现（core/ui/ToolboxWebView.kt、buildConfig 基础地址、debug 明文白名单，构建/Lint 0 错误 24 警告），客户端 WEB 打开路径已接线（ToolDetailPage 按 ToolMode.WEB 走 ToolboxWebScreen），官网已托管 /tools/qr/ 网页版占位页（本地 200，admin 镜像已重建），首个 WEB 工具 qr_studio 已登记（V16 / schema v16，API total=12 且含 mode=WEB，/tools/qr_studio/ 返回 200）；页面真实生成功能已完成（文本、Wi-Fi 含 WPA3/EAP、vCard 多值，PNG/JPEG/SVG 导出；payload.js 经 node 测试通过，页面与资源均 200）；qr_studio 已注册进客户端 ToolRegistry（WEB，中英资源与 V16 描述一致，App 内可见并按容器打开）；目录远程合并与自动更新已接线（ToolCatalogClient + ToolRegistryStore，各入口观察共享快照，启动后台请求一次）；下一项为目录网络契约/读取预算、缓存或浏览器/真机 WebView 验收（DATABASE 的 V16 表行已补齐），之后按路线图处理扫码历史、裁剪、vCard/多值、事件时区与提醒、SVG/JPEG 等剩余缺口并推进设备验收。剩余设备、英语切换、剪贴板、相机授权与 Wi-Fi 连接、PNG 保存/分享与扫码识别率、结构化载荷在第三方应用中的导入、多码耗时与相机收集节奏、符号标签布局验收，页面动画的关闭系统动画/TalkBack/低端设备验收，以及仓库自动化回归测试缺口。完成设备验收前不要把 Phase 2 标记为全部验收通过，也不能直接进入 Auth & Sync。
- 生产域名规划：官网/后台 tool.zhzgo.cn，API toolapi.zhzgo.cn。
- 网页版工具承载规则已确定并写入 PROJECT_SPEC Decision 019、两处 AGENTS、docs/TOOL_DEVELOPMENT_GUIDE.md、Android UI 规范与部署说明：复杂或体积大的工具可放官网 `/tools/<code>/`，App 用系统 WebView（Chrome 内核）打开；`WEB` 模式、数据库 CHECK 迁移与 API/DTO 尚未实现，首个网页版工具落地时同步。
- 本地环境：Docker 数据在 D:\DockerData；Compose 项目 cangshuo-toolbox-local（server 8081、admin 8088、mysql 3307、redis 6380）。
- 2026-10-04 已启动 Docker Desktop Linux 引擎（29.8.1）并重建本地 server 镜像；当前已执行到 V14，GET /tools 与 Nginx 联调通过、五个容器健康。接手时仍以 `docker compose ps` 与本机实际状态为准。

## 使用说明

2026-10-05 最新增量（覆盖上方历史状态）：目录读取校验、Room v3 WEB 快照和应用级去重刷新已完成；启动先恢复缓存，前台 5 分钟、手动 2 秒。真实 Server schema v17（含 V17 描述修正）、目录 12 项；Android 16 验证更新、失败重启恢复纯远程探针、搜索、恢复移除探针及网页打开。浏览器验证 Unicode/文本/Wi-Fi EAP/vCard 多值、复制、容量错误和恢复。修复网页 UTF-8、旧二维码残留、版本展示、换行和旧缓存；WebView 补齐严格来源策略及分层 SAF 导出。最终 96/96 Android 测试、24 SQLite、22 网页回归通过，离线门禁 2 分 44 秒、Lint 0 错误/22 既有警告，Nginx 通过、页面 200/no-cache。手机保存、完整历史/错误/TLS 未验收；最后禁缓存 APK 因 USB 断连尚未更新安装。用户要求加快，停止追加设备场景，补验留到发布阶段，不再循环等待设备或阻塞功能开发；不要勾选完整 Phase 2。二维码工具的原生功能已全部落地：扫码历史、事件时区/提醒、手动框选识别、线性条码生成（CODE 128/39/93、EAN-13/8、UPC-A、ITF、Codabar）、密集混合多码线性补扫、PNG/JPEG/SVG 导出、邮件抄送/密送、WPA3 与企业 Wi-Fi（EAP）、MECARD/vCard 3.0 多值联系人和 qr_studio 描述修正（V17/schema v17）：141/141 Android 测试、24+12 项主机 SQLite 检查通过，构建/Lint 0 错误/22 既有警告，本地 server 已应用 V17；Phase 2 功能已全部完成，只差设备与第三方应用验收（当前手机未连接），实施前读 docs/QR_TOOL.md、docs/QR_HISTORY.md 与相关业务源码。详细范围见 docs/WEB_TOOL_ACCEPTANCE_2026-10-05.md。现有混合工作区未提交，不擅自全量提交/推送。

- Codex 桌面版：把会话工作目录设为 D:\codeStudy\code\cangshuo-tool 后，项目 AGENTS.md 会自动加载，但仍建议发一次开场消息说明当前任务。
- 同一会话内换模型：上下文自动延续，不需要特殊说明，换完后直接说「继续」即可。
- 新窗口最容易出问题的是 AI 凭记忆假设进度。第一句一定要让它先读 ROADMAP 再动手；仓库文档比任何口述摘要都新。
- 如果新会话没有本机文件访问能力，可先读 GitHub 中已提交的资料；未提交工作区不会出现在远端，需额外提供本机改动及审查/修复进度。
