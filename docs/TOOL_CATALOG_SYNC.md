# Android 远程目录更新与请求校验

对应 Roadmap 中网页版承载的目录自动更新和网络校验子任务。启动先使用内置工具；远程 WEB 目录完整到达并通过校验后，首页、搜索、收藏和最近使用读取同一个注册中心快照，无需切换标签或手动刷新。工具打开时也从当前快照解析定义。

## 数据链路

```text
RequestWebToolCatalogSyncUseCase（启动 / 前台间隔 / 手动，共享请求）
  → 首次 RestoreWebToolCatalogUseCase → Room 有效快照
  → ToolCatalogClient : ToolCatalogSource
  → 有界字节读取 → CatalogJsonReader → 全部页面校验
  → RefreshWebToolCatalogUseCase → WebToolDefinition
  → ToolRegistryStore.replaceRemoteWebTools
  → StateFlow<ToolRegistry>
  → 各 Repository → UseCase → ViewModel → Compose
```

`ToolRegistry` 继续是不可变集合，`ToolRegistryStore` 是应用级共享持有者。StateFlow 提供线程安全的发布和当前值读取；同步目录读取只访问内存，不执行网络或 Room I/O。首页与搜索在 `viewModelScope` 观察初始及后续快照；清除 ViewModel 后取消观察。保留初次同步读取，同时处理网络结果早于观察器订阅的情况，不用 `drop(1)` 丢弃首个快照。

收藏和最近使用 Repository 将 Room DAO Flow 与目录快照 `combine`。目录变化本身会触发元数据重新解析，不需要用户再次收藏、打开工具或写数据库。失去远程定义的记录保留编码、时间及使用次数，沿用既有缺失元数据的展示规则。

## 开源源码参考

查阅日期：2026-10-04。只采用设计思路，没有复制第三方实现。

| 具体来源 | 实际阅读与采用 | 适配差异 |
| --- | --- | --- |
| [Android Now in Android：OfflineFirstNewsRepository.kt](https://github.com/android/nowinandroid/blob/main/core/data/src/main/kotlin/com/google/samples/apps/nowinandroid/core/data/repository/OfflineFirstNewsRepository.kt)（main 分支，可变化，Apache-2.0） | `getNewsResources` 从可观察的数据源映射领域数据，网络同步通过更新共享数据源影响订阅者；采用统一读取来源与更新通知的思路 | 本轮使用内存目录快照；收藏/最近使用继续用已有 Room 表，不实现该示例的增量同步或通知机制 |
| [kotlinx.coroutines 1.9.0：StateFlow.kt](https://github.com/Kotlin/kotlinx.coroutines/blob/1.9.0/kotlinx-coroutines-core/common/src/flow/StateFlow.kt)（Apache-2.0） | 阅读 `MutableStateFlow.value`、`updateState` 与 `collect` 的发布、线程同步、当前值重放和合并行为；采用只读 StateFlow 暴露快照 | 保持项目已有协程 1.9.0；每次替换构造新的注册中心，不修改已发布的集合，不依赖每个中间快照都被观察 |
| [OkHttp 4.12.0：Http1ExchangeCodec.kt](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/internal/http1/Http1ExchangeCodec.kt)（Apache-2.0） | 阅读 `FixedLengthSource.read` 对异常 EOF 的处理及流关闭路径；采用流式读取、长度核对和所有退出分支关闭资源 | 保留项目已有 HttpURLConnection，不引入 OkHttp；应用层限制已读取字节，不直接借用底层 HTTP framing 实现 |
| [OkHttp 4.12.0：RealCall.kt](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/internal/connection/RealCall.kt)（Apache-2.0）、[OpenJDK 21：URLConnection.java](https://github.com/openjdk/jdk/blob/jdk-21%2B35/src/java.base/share/classes/java/net/URLConnection.java)（GPL-2.0 + Classpath Exception） | 阅读 RealCall 的总调用超时/取消和 URLConnection 的读取超时定义；采用跨页面的调用期限，不能用单次 readTimeout 代替总期限 | 使用独立守护定时器、原子完成标志与解析检查点，超时/取消后在 IO 线程尽力断开连接；不声称具有 OkHttp 所有平台取消保障 |
| [Gson 2.13.2：JsonReader.java](https://github.com/google/gson/blob/gson-parent-2.13.2/gson/src/main/java/com/google/gson/stream/JsonReader.java)（Apache-2.0） | 阅读数字扫描、字符串转义和严格性检查路径；采用严格 token 语法及数字原文校验，避免 opt 系列方法的类型转换 | 使用独立的有界 Kotlin 目录解析器，不引入 Gson；目录重复字段、整数小数/指数写法、未配对代理项额外拒绝，未知字段在预算内允许 |

上述网络源码于 2026-10-04 查阅，网络实现及验证在 2026-10-05 收尾。复用了项目既有严格 JSON 扫描的语法处理思路；目录读取器位于 core/network，避免 core 依赖 feature/json，并按目录契约拒绝重复字段。

## 网络读取与契约

| 项目 | 当前行为 |
| --- | --- |
| 地址与 HTTP | 基础地址来自构建配置，仅接受 http/https 且不含用户凭据、查询或片段；正式构建使用 HTTPS，debug 沿用现有明文白名单。只接受 HTTP 200，不跟随重定向，不读取错误响应正文，不使用 HTTP 缓存 |
| 类型与编码 | 请求 application/json、identity；响应必须为 application/json，charset 若声明只能是 UTF-8/UTF8，Content-Encoding 若声明只能是 identity；UTF-8 解码使用 REPORT，非法字节直接失败 |
| 每页读取预算 | 最多 1,000,000 字节；Content-Length 已知且超预算时不开启正文流。未知长度最多多读 1 字节用于判定超限，该字节不追加到输出；有效已知长度必须与实际读取字节数一致 |
| 整次预算 | 最多 3,000,000 字节、3 页、每页 100 条，总数最多 300（包含非 WEB 条目）；不会把前三页的部分目录发布成成功结果 |
| 超时 | 每次连接 5 秒、每次阻塞读取 8 秒；从获得客户端请求锁起，全部页面和解析共用 20 秒期限，持续滴流不会重置总期限。等待锁支持协程取消；没有自动重试 |
| JSON 语法 | 单个完整 JSON 文档，标准空白和转义；拒绝注释、单引号、尾逗号、多根、BOM、重复成员、未配对 UTF-16 代理项、非法 UTF-8。已知整数字段仅接受整数字面量，不接受字符串、小数或指数写法，Long/Int 越界失败 |
| 解析预算 | 每页最多 16 层容器、20,000 个值及成员名 token、每个解码字符串 4096 个 UTF-16 单元、数字字面量 64 字符；每条记录最多 64 个关键词，每个最多 512 个 UTF-16 单元。预算内未知字段允许，但同样完整解析并受限 |
| 响应封装 | code 必须为整数 0，message 为非空白字符串，data/records 为正确类型；traceId 为 32 位小写十六进制且与 X-Trace-Id 完全一致，缺失不回退默认值 |
| 元数据 | 所有约定字段必须存在、类型正确，icon 是必须存在的可空字符串；沿用 ToolMetadata 编码、长度、类别、模式、状态和版本校验，目录记录必须 ENABLED。无效条目使整次请求失败，不静默跳过 |
| 分页完整性 | 响应 page/pageSize 与请求一致，各页 total 相同，记录数量与 total/偏移一致；所有模式一起计数、检查全目录 code 唯一和 sortOrder/code 顺序，验证后才筛选 WEB |
| 发布与失败 | RefreshWebToolCatalogUseCase 只在整个请求及所有 DTO 转换完成、协程仍有效时一次性发布；NETWORK/TIMEOUT/HTTP/INVALID_RESPONSE/RESOURCE_LIMIT/INCOMPLETE 均保留旧快照，首次失败保留内置目录。取消继续向上传播，正常成功空目录才移除旧远程项 |
| 错误信息 | 固定错误类型，没有响应正文、URL、原始异常消息或 cause；不记录服务端 message、工具内容、凭据或敏感原文 |

客户端实例用 Mutex 串行请求；期限定时器取消后从队列移除，读取/解析均检查取消和期限，流关闭及连接断开使用 finally。期限到达会完成等待方并丢弃后到结果，连接清理由 IO 线程尽力执行。底层提供者的 DNS 或不可中断 I/O 不保证立即终止，仍需 Android 网络提供者的设备验收；这不允许过期结果更新目录。

当前 API 仅保证单页 count/records 在同一服务端事务内一致，没有跨页版本或快照 token。客户端能发现总数变化、缺页、重复编码和逆序；总数不变且仍满足这些条件的跨页内容变化无法完全识别。本轮不新增服务端快照协议或 API 字段。

## 功能与边界

| 场景 | 行为 |
| --- | --- |
| 无网络或请求失败 | 保留当前目录，不把失败当成成功空目录，不记录原始响应或异常 |
| 远程条目到达 | 首页列表、分类数量、常用及推荐重新计算；当前搜索查询重新匹配 |
| 用户已设置筛选 | 保留当前标签、分类、搜索文本、收藏/最近使用排序权重和导航状态 |
| 已有内容的目录更新 | 直接更新结果，不把已有内容切成全页 Loading；初次加载与手动重试仍沿用原状态 |
| 同编码冲突 | 内置定义优先，包括内置 WEB 定义；远程条目不能替换已打包的实现 |
| 非 WEB 远程定义 | Store 拒绝整次替换，当前快照不变；客户端仍只将已校验的 WEB DTO 转成通用定义 |
| 重复远程编码 | HTTP 获取在筛选 WEB 前拒绝整次结果；底层 Store 合并仍保留第一条定义，注册中心按 sortOrder/code 排序 |
| 后续成功快照删除条目 | 每次从内置目录重新合并，旧的纯远程条目不会残留；成功空列表恢复内置目录 |
| 收藏或最近使用不变 | 目录快照触发重映射，不更改数据库内容或使用次数 |
| ViewModel 已销毁 | 停止观察；下一次创建时直接读取最新快照 |

网络校验任务当时未新增 API/权限/数据库/Flyway；后续缓存任务新增 Room v3，联调补充 INTERNET 权限。见 [缓存](TOOL_CATALOG_CACHE.md)和[刷新](TOOL_CATALOG_REFRESH.md)。已打开工具保持导航状态，下一次打开从最新目录解析，不强制关闭当前工具。

## 目录观察回归与门禁

`android/app/src/test/java/com/cangshuo/toolbox/core/tool/ToolCatalogUpdatesTest.kt` 包含 12 个 JUnit 用例：内置立即可读、活跃首页更新及计数、订阅前到达、活跃搜索及权重保留、元数据变化与移除、后台更新不闪 Loading、内置冲突优先、拒绝非 WEB、替换/去重/排序、收藏重映射、最近使用重映射和观察取消。

这 12 个测试使用真实 Store、Repository、UseCase、首页/搜索 ViewModel；Room DAO 使用受控 Flow 替身，以确认数据库没有再次发射或写入时目录也会更新。它们未执行真实 SQLite、HTTP、资源语言切换、WebView 或设备 UI。网络测试范围另见下方。

仅新增测试依赖 JUnit 4.13.2 和与现有协程同版本的 kotlinx-coroutines-test 1.9.0。CI 的 Android 任务执行 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`，保存单元测试 HTML/XML 报告，保留 7 天；托管执行结果以实际 GitHub 运行记录为准。

## 2026-10-04 目录观察验证

2026-10-04 本机门禁：Android Studio JBR 21，`:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 构建成功（7 分 22 秒，54 个任务执行）；JUnit 为 12/12，通过用时 0.455 秒，0 失败、0 错误、0 跳过。Lint 为 0 错误、25 条警告，相比此前 24 条多出的 1 条是 kotlinx-coroutines-test 的新版本可用提示；测试库刻意保持与现有协程声明版本一致，没有升级业务依赖。源码及测试没有新增 Lint 问题。actionlint 1.7.12 校验工作流退出码 0；`git diff --check` 通过。

报告位置沿用 `android/app/build/reports/tests/testDebugUnitTest/index.html` 与 `android/app/build/test-results/testDebugUnitTest/`；APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`，Lint 报告位于 `android/app/build/reports/lint-results-debug.html`。后续构建会更新这些文件。2026-10-04 这次门禁没有执行 HTTP、真实 Room、浏览器、真机或 GitHub 托管 CI。

## 2026-10-05 网络校验验证

新增 39 个持久化 JUnit 测试，与原有 12 个目录观察测试一起执行：

| 测试类 | 数量 | 实际覆盖 |
| --- | --- | --- |
| CatalogJsonReaderTest | 13 | 中文及完整字段、有效空目录和未知字段、业务错误和缺失字段、traceId、禁止类型转换、元数据边界、全部模式、整数精度/溢出、非标准语法/尾部内容、重复成员、Unicode 转义/代理项、解析预算与期限检查点 |
| ToolCatalogClientTest | 19 | 全模式计数后筛选、所有页面成功、合法空目录、分页/总数/数量/重复/排序异常、超页数及 Long 总数、第二页失败、Content-Length 提前拒绝、未知长度只多读 1 字节、UTF-8 字节预算与刚好达到上限、累计预算、短读/长度不符/非法 UTF-8、HTTP 错误/重定向、类型/编码/trace、固定错误、请求配置、持续滴流总期限、阻塞读取取消，以及本机 HTTP 固定长度/分块/读取超时 |
| RefreshWebToolCatalogUseCaseTest | 7 | 全部预期失败类型保留同一旧快照、成功替换和内置优先、有效空结果、后续无效记录不部分发布、非 WEB/关闭记录拒绝、取消及结果到达前已取消、整个获取完成前目录仍可读 |
| ToolCatalogUpdatesTest | 12 | 既有首页、搜索、收藏、最近使用、快照和观察取消回归 |

采用可注入 HttpURLConnection 替身精确控制长度、读取字节数、关闭资源和分页；额外用 JBR 内置 HttpServer 监听本机 127.0.0.1 临时端口，通过真实 HttpURLConnection 验证固定长度、chunked UTF-8 和阻塞读取超时。慢速滴流与阻塞取消使用受控 InputStream，不依赖公网。测试服务在 finally 停止；没有新增业务或测试依赖。

本机 Android Studio JBR 21 离线执行 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` 成功，用时 2 分 22 秒，54 个任务（21 执行、33 已最新）。JUnit 51/51 通过，0 失败、0 错误、0 跳过，各 suite 用时合计 1.252 秒。Lint XML 为 0 错误、22 条警告，目录客户端/解析器/刷新 UseCase/相关测试没有 Lint 问题；本轮没有依赖升级，不把版本可用提示数量变化作为代码修复收益。APK 为 17,320,285 字节，输出及报告位置均在 D 盘项目目录，沿用上方路径。

`git diff --check` 通过，新增任务文件的空白/冲突标记检查通过；本轮 D 盘构建临时目录已清理，保留其他任务的缓存和文件。没有执行 Android 平台网络提供者、实际服务端客户端联调、SQLite、浏览器、真机/WebView 或 GitHub 托管 CI；本机 HTTP 测试不能代替这些验收。跨页快照缺失与底层 I/O 取消边界见上文，整项网页版承载及 Phase 2 设备验收继续保持未完成。

## 后续工作

- 2026-10-05 已实现启动/前台 5 分钟/手动 2 秒去重刷新，见 [刷新策略](TOOL_CATALOG_REFRESH.md)。
- 2026-10-05 已新增 Room v3 持久缓存及后台恢复；读取预算、损坏/来源/版本校验、74 个测试和迁移范围见 [缓存说明](TOOL_CATALOG_CACHE.md)。上文日期记录保留当时的验证范围。
- 当前页数和体积上限明确拒绝超限目录；以后目录超过 300 条时需另行设计增量、快照或更大的有界同步协议。
- 浏览器表单、Unicode、复制、容量错误恢复和真机缓存/失败保留/恢复删除/搜索/网页打开已验证；手机保存、完整历史/错误/TLS/语言矩阵未完成。用户要求加快后停止追加验收，见 [记录](WEB_TOOL_ACCEPTANCE_2026-10-05.md)，补验不阻塞下一项功能。
