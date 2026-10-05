# URL 编解码器

唯一编码 `url_codec`，分类 `DEV`，模式 `LOCAL`，排序值 140。`UrlCodecToolDefinition` 注册到 ToolRegistry，首页、目录、搜索与推荐共用同一份定义；无需账号、网络或新增 Android 权限。服务端目录登记由 V8 迁移完成，本轮没有修改迁移或接口。

2026-10-04 本轮修复：移除 `java.net.URLEncoder` / `URLDecoder` 混用表单语义的路径，改为严格单层百分号编解码；修复完整 URI 模式的加号往返；组件模式按 RFC 3986 非保留字符集；新增表单值处理类型、严格 UTF-8 校验、错误位置、后台串行执行、资源预算、取消/重试与中英反馈。

## 实际开源参考

查阅日期：2026-10-04。以下链接为当日取得的分支文件，不是固定提交；没有把项目名称或 README 当作完整业务源码参考。

| 来源与实际阅读范围 | 采用思路 | Android 适配与差异 |
| --- | --- | --- |
| [WHATWG URL Standard](https://url.spec.whatwg.org/) 第 1.3 节 percent-encode set、第 5 节 `application/x-www-form-urlencoded` parser/serializer | 表单序列化只保留字母数字与 `* - . _`、空格写 `+`；解析时先把 `+` 按字节换成空格再百分号解码；百分号编码输出 `%HH` | 自行实现字节级 UTF-8 编解码；只处理一个表单值，不拆分或重组 `&` / `=` 元组 |
| [jsdom/whatwg-url `lib/urlencoded.js`](https://github.com/jsdom/whatwg-url/blob/main/lib/urlencoded.js)（sha `ed53310bbede736c7cd79d7839605f4e2e581d2e`）：`parseUrlencoded`、`serializeUrlencoded`、`replaceByteInByteSequence` | 处理顺序按规范固定为“`+` → 空格 → 百分号解码 → UTF-8 解码”；序列化时再统一编码 | 未引入 Node 依赖，改为 Kotlin 有界扫描；不实现整段查询参数列表 |
| [CyberChef `URLEncode.mjs`](https://github.com/gchq/CyberChef/blob/master/src/core/operations/URLEncode.mjs)（sha `99eec91d7a37d3c44f8d2e67ee76d4d5c4d4a7c6`）：`safeChars` 两档与 `%HH` 大写输出 | 参考“保留字符集 / 全编码”两档的组织方式 | 扩展为组件、URI 整体、表单值三档；CyberChef 默认把 `%` 当安全字符，本项目编码始终把已有 `%` 转义为 `%25`，保证原文语义 |
| [CyberChef `URLDecode.mjs`](https://github.com/gchq/CyberChef/blob/master/src/core/operations/URLDecode.mjs)（sha `bb6c06120bb82e83bde4258b7091d159b8147c8e`）：`plusIsSpace` 开关、失败反馈与 `unescape` 回退 | 参考“加号是否按空格处理”的显式选项思路 | 三种类型各自固定语义，不再提供会改变结果的隐含开关；不采用 `unescape` 回退，避免把 `%uXXXX` 等非标准写法静默解释；非法 `%HH` 与无效 UTF-8 返回带位置的错误 |
| [IT-Tools `url-encoder.vue`](https://github.com/CorentinTh/it-tools/blob/main/src/tools/url-encoder/url-encoder.vue)（sha `190251909b27d6821013f55705de2e9caba9dda4`）：双向卡片、`encodeURIComponent` / `decodeURIComponent`、校验提示与复制 | 参考双向操作、即时结果、无效输入校验与复制反馈 | 改为单输入区加模式切换，复用项目最大 600dp、键盘 Insets 与共享加载；错误不吞掉为固定占位文本，而是显示本地化原因与位置 |
| [RFC 3629](https://www.rfc-editor.org/rfc/rfc3629) 第 4 节 UTF-8 ABNF 与 [RFC 3986](https://www.rfc-editor.org/rfc/rfc3986) 第 2.3 节非保留字符 | 组件安全字符取 `A–Z a–z 0–9 - . _ ~`；逐字节校验 UTF-8 前导/继续字节范围 | 自行实现码点与字节转换；拒绝过长编码、代理区码点与超出 U+10FFFF 的序列，不静默替换 |

`URLEncoder` / `URLDecoder` 的 Java 文档语义与 `decodeURIComponent` 不同（前者按表单应用），本次不再作为实现路径；`unescape` 回退路径未采用；没有复制第三方业务代码，也没有新增第三方许可证资产。

## 实现分层

```text
UrlCodecRoute / UrlCodecScreen
  → UrlCodecViewModel（SavedStateHandle、请求版本、取消与重试）
  → EncodeUrlUseCase / DecodeUrlUseCase
  → UrlCodecRepository / LocalUrlCodecRepository
  → UrlOutput + UrlWorkGuard + 严格 %HH / UTF-8 扫描
```

两个 UseCase 均为 `suspend`；编解码在 `Dispatchers.Default` 的互斥区执行，UI 不运行字节或码点循环。原 `java.net.URLEncoder` / `URLDecoder` / `URI` 路径已移除。ViewModel 使用请求版本过滤旧任务结果，输入变化有 150ms 防抖，取消后不再写回旧结果。

## 处理类型与规则

三种类型同时适用于编码和解码，界面标签为“组件”“URI 整体”“表单值”。

### 编码

| 类型 | 安全字符（不转义） | 实际行为 |
| --- | --- | --- |
| 组件 | `A–Z a–z 0–9 - . _ ~` | 空格 → `%20`，`+` → `%2B`，`%` → `%25`；适合路径片段与独立参数 |
| URI 整体 | 组件安全字符，加 `: / ? # [ ] @ ! $ & ' ( ) * + , ; =` | 保留 URI 结构符；空格 → `%20`；已有 `%` → `%25`；不校验链接是否有效 |
| 表单值 | `A–Z a–z 0–9 * - . _` | 空格 → `+`，`+` → `%2B`，`~` → `%7E`；只处理一个值，不拆分 `&` 与 `=` |

- 编码把输入当原文：已有 `%20` 会变成 `%2520`，不会先解释再编码。
- 十六进制统一大写 `%HH`；非 ASCII 字符按 UTF-8 生成 1–4 个 `%HH`。
- “URI 整体”保留 `+` 原样，解码时也保持，因此 `https://example.com/?q=a+b` 往返不变。
- 不规范化 scheme/域名大小写、默认端口、路径点段或已有百分号的十六进制大小写。

### 解码

- 单层解码：只处理一层 `%HH`；`%2520` → `%20`，不会继续变成空格。
- 组件：所有 `%HH` 还原为对应字节；原始 `+` 保持 `+`。
- URI 整体：`%HH` 对应 URI 保留字符（如 `%2F`、`%26`、`%2B`、`%3D`、`%23`）时保留原始转义写法，避免引入新的分隔符；其他字节正常解码，原始 `+` 保持 `+`。
- 表单值：先把原始 `+` 还原为空格，再解码 `%HH`；`%2B` 还原为 `+`。仅处理一个值，不解析整段查询参数。
- 严格校验：`%` 后必须恰好两位 ASCII 十六进制，否则返回错误与输入位置（1 起算）。
- 严格 UTF-8：按 `0xC2–0xDF`、`0xE0–0xEF`、`0xF0–0xF4` 与 `0x80–0xBF` 继续字节校验，拒绝缺失/截断字节、过长编码、代理区 `U+D800–U+DFFF` 与大于 `U+10FFFF` 的码点；不静默替换为 `U+FFFD`。
- 输入中的未配对 UTF-16 代理字符在编码前直接报错，避免生成无效 UTF-8。
- 只支持 UTF-8 百分号数据；不自动按 GBK、Latin-1 等其他字符集猜测，也不把解码结果继续按 URL 处理。

### 资源与状态边界

- 输入、输出上限均 100000（UTF-16 长度）；达到输出上限时整体报错，不产生部分结果。
- 协作取消与 2 秒工作预算：循环每 1024 步检查协程取消与时间；取消、超时、内存不足和未知失败都有独立状态与重试入口。
- 结果预览最多显示前 8000 个 UTF-16 单元，避免超长文本一次性排版；复制与“结果作为输入”始终使用完整结果，截断时显示提示。
- 输入框拒绝超过上限的新文本并保留原输入，提示缩短后重试。
- 空输入为“等待输入”状态，不是错误；处理期间可使用“取消处理”。

### 功能边界

- 不解析 URL、不校验字符合法性、不做 IDN/Punycode、不补默认端口、不合并重复参数。
- 不做多层解码、不自动识别编码类型、不提供其他字符集。
- 不做批量处理、文件导入导出、历史记录或云同步。
- 表单模式只处理一个值；整段 `name1=v1&name2=v2` 的拆分与重组不在本工具范围。

## 界面与交互

- 模式芯片“编码 / 解码”，类型芯片“组件 / URI 整体 / 表单值”，切换后立即重算。
- 类型说明随方向切换；固定提示单层解码语义与 `%2520` 示例。
- 输入卡提供粘贴、示例、清空；错误态高亮输入框；显示 `长度 / 100000`。
- 结果卡使用等宽字体、可选择文本、完整复制、结果作为输入并切换方向；无结果时显示等待文案。
- 处理中复用 `ToolboxLoadingState`；错误卡片按类型显示中英原因、输入位置与“重新处理”。
- 所有用户可见文案走中英资源，没有硬编码文本。

## 服务端目录登记

`V8__seed_url_codec_tool.sql` 保持不变：code `url_codec`、分类 `DEV`、模式 `LOCAL`、排序 140、`ENABLED`、推荐位；关键词覆盖 URL、URI、urlencode、urldecode、编码、解码、转义、链接、参数等。本轮没有新增迁移、API、依赖或权限，API 契约文档无需变更。

## 验证与验收

2026-10-04 验证记录：

| 检查 | 结果 | 能证明的范围 |
| --- | --- | --- |
| 离线 `:app:assembleDebug :app:lintDebug` | BUILD SUCCESSFUL（完整构建 83 秒；随后增量确认 15 秒） | 源码可编译并通过 Lint 错误门禁；0 错误、24 条警告，URL 源码与新增资源无 Lint 问题；唯一编译提示是项目既有的 `LocalClipboardManager` 弃用告警，非本工具新增模式 |
| 中英资源 key | URL 相关各 44 个，集合一致、无重复、引用齐全 | 切换英语时不会因缺 key 回退中文 |
| `git diff --check` | 通过 | 已跟踪差异没有空白错误 |

未执行：单元/仪器测试、业务样例运行、Preview 渲染、真机或模拟器、英语系统切换、剪贴板与超长文本交互。构建与静态检查通过不代表下列行为已经运行验收。

建议人工样例（待设备执行）：

1. 组件编码 `沧烁 工具箱&test=1` → `%E6%B2%A7%E7%83%81%20%E5%B7%A5%E5%85%B7%E7%AE%B1%26test%3D1`，再解码恢复原文。
2. 组件编码 `a!()*'~` → `a%21%28%29%2A%27~`（`~` 保留）。
3. URI 整体 `https://example.com/?q=a+b` 编码/解码往返保持 `+`；`https://example.com/a b?q=沧烁` 编码后空格为 `%20`。
4. URI 整体解码 `https://example.com/?q=a%2Bb&x=1` 保持 `%2B`，解码 `%E6%B2%A7%E7%83%81` 恢复中文。
5. 表单值 `沧烁 工具箱+~*` → `%E6%B2%A7%E7%83%81+%E5%B7%A5%E5%85%B7%E7%AE%B1%2B%7E*`，再解码恢复原文。
6. 错误输入 `%2`、`%GG`、`%E4%B8`、`%ED%A0%80` 分别显示非法百分号或无效 UTF-8 与位置；`%2520` 单层解码为 `%20`。
7. 空输入显示等待；URI 整体解码不把 `+` 变成空格。

剩余风险：有效 UTF-8 校验失败时位置指向相关字节起点，不是精确字符列；8000 字符预览只影响显示，复制使用完整结果；超长输入的实际性能和取消响应仍需设备验证。
