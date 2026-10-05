# 文本处理工具

唯一编码 `text`，分类 `TEXT`，模式 `LOCAL`，排序值 200。通过 `TextToolDefinition` 注册到现有 ToolRegistry。全部处理在设备本地进行，无需登录、网络或新增 Android 权限。

2026-10-04 本轮修复范围：文本工具的主线程正则执行、资源限制、取消和结果归属，同时补齐统计/转换规则、查找替换选项及共享加载。没有修改其他工具、API 契约或数据库迁移。

## 实际开源参考

查阅日期：2026-10-04。RE2/J 使用已确认的 `re2j-1.8` tag；IT-Tools 链接为当日读取的 `main` 分支，内容可能继续变化。下表只记录实际取得并阅读的文件，不表示审查了整个仓库。

| 来源与具体文件 | 采用的思路 | 本项目适配与差异 |
| --- | --- | --- |
| [RE2/J 1.8 README](https://github.com/google/re2j/blob/re2j-1.8/README.md) | 采用 RE2/J 避免 Java 回溯引擎的执行风险，明确语法兼容范围 | 新增 `com.google.re2j:re2j:1.8`；用户模式不再交给 Kotlin/Java Regex。不支持前后查找或模式反向引用，不承诺兼容全部 Java/JavaScript 正则 |
| [Matcher.java](https://github.com/google/re2j/blob/re2j-1.8/java/com/google/re2j/Matcher.java) | 读取查找推进、分组位置及替换模板处理 | 自行解析替换模板并在每次追加前检查输出长度；零宽匹配按 Unicode 码点推进，避免按单个 UTF-16 单元拆开代理对；未参与匹配的分组展开为空字符串 |
| [MachineInput.java](https://github.com/google/re2j/blob/re2j-1.8/java/com/google/re2j/MachineInput.java) | 查阅 CharSequence 输入和前缀查找路径 | 传入带取消/时间检查的 CharSequence，在引擎读取输入时执行协作检查；没有声称任意引擎内部指令均可立即中断 |
| [Parser.java](https://github.com/google/re2j/blob/re2j-1.8/java/com/google/re2j/Parser.java)、[Compiler.java](https://github.com/google/re2j/blob/re2j-1.8/java/com/google/re2j/Compiler.java)、[Pattern.java](https://github.com/google/re2j/blob/re2j-1.8/java/com/google/re2j/Pattern.java) | 查阅计数重复解析、编译展开、标志和 programSize/groupCount 接口 | 增加编译前的长度、嵌套及保守展开检查，编译后再限制程序规模/分组。预检查不是完整语法解析器，最终语法仍由 RE2/J 判断 |
| [RE2/J LICENSE](https://github.com/google/re2j/blob/re2j-1.8/LICENSE) | 保留依赖的 BSD 许可证与署名 | 原文存放在 `android/app/src/main/assets/licenses/re2j-1.8.txt`，随 APK 打包 |
| [IT-Tools regex-tester.vue](https://github.com/CorentinTh/it-tools/blob/main/src/tools/regex-tester/regex-tester.vue) | 参考大小写、多行锚点、dotAll 选项与模式校验交互 | Android 使用 RE2/J，而非网页 JavaScript 引擎；当前显示替换结果和匹配数量，没有增加独立匹配表、匹配高亮或浏览器功能 |
| [IT-Tools text-statistics.vue](https://github.com/CorentinTh/it-tools/blob/main/src/tools/text-statistics/text-statistics.vue)、[text-statistics.service.ts](https://github.com/CorentinTh/it-tools/blob/main/src/tools/text-statistics/text-statistics.service.ts) | 参考多维统计与 CRLF/CR/LF 换行处理 | 本项目用 Unicode 码点计数，明确英文词项和 Han 字符定义；按码点累加 UTF-8 字节长度，避免生成整个编码副本 |

RE2/J `Machine.java` 本轮网页读取未成功，没有将其记为已读源码。本轮没有直接复制 IT-Tools 业务代码，也没有为其他工具升级依赖。

## 实现分层与状态

```text
TextRoute / TextScreen
  → TextViewModel
  → ComputeTextStatisticsUseCase / TransformTextUseCase / FindReplaceUseCase
  → TextRepository / LocalTextRepository
  → 有界文本处理 + RE2/J
```

- Repository 的三个方法均为 suspend，并在 `Dispatchers.Default` 执行；UI 和 ViewModel 不运行正则或统计循环。
- ViewModel 在编辑后等待 150 ms 再处理；选项变化立即重新处理。每次请求先取消旧 Job，并以请求版本阻止旧结果覆盖当前输入。
- 修改已接受的输入/选项后立即清除旧输出、匹配数量和结果可用状态。失败、取消或超限时不提供半成品结果。
- Repository 使用一个互斥锁串行执行工作，避免取消尚未结束时叠加同一工具的处理。
- 支持共享加载、取消、重试、样例、粘贴、清空、复制完整结果和“结果作为输入”。后者是回填输出，不是双向交换。
- 输入、查找/替换文本及选项使用 SavedStateHandle 恢复；统计和输出重新计算，不单独保存。恢复时拒绝超长保存值，不静默截断。没有新增文本历史、文件读写、导出或分享。
- 不记录用户原文、模式、替换文本或原始异常。SavedState 是 Android 状态恢复机制，不是加密保密存储。

## 功能覆盖与语义

### 七项统计

| 指标 | 规则 |
| --- | --- |
| 总字符数 | Unicode 码点数量。合法代理对计为一个；组合附加符号各自计数，不是用户可见字形簇数量 |
| 无空白字符数 | 排除 `Character.isWhitespace` 或 `Character.isSpaceChar` 判断为真的码点 |
| 英文词项 | ASCII 字母、数字或下划线开始词项；词项中的连字符/撇号可连接两侧内容，其余字符分隔。不是语言学分词 |
| Han 字符 | 按 `Character.UnicodeScript.HAN` 判断；不能等同于全部中文标点、日文、韩文或 CJK 字符 |
| 总行数 | 识别 CRLF、CR、LF；CRLF 为一个换行，保留末尾空行，空输入为 0 行 |
| 非空白行数 | 至少包含一个非上述空白码点的行 |
| UTF-8 字节数 | 对有效 Unicode 码点按 1/2/3/4 字节累加，包含换行和空白 |

Unicode 脚本与大小写表由设备系统提供，不承诺跨所有 Android 版本的 Unicode 新增字符结果完全一致。编辑器长度上限使用 UTF-16 单元，统计字符数使用码点，两者可能不同。

### 转换与清理

- 九种大小写/命名：UPPERCASE、lowercase、Title Case、Sentence case、camelCase、PascalCase、snake_case、kebab-case、CONSTANT_CASE。
- 大小写使用 `Locale.ROOT`，避免跟随系统语言导致土耳其语等区域差异。句首转换保留其余正文大小写，并识别 `. ! ? 。 ！ ？` 与换行。
- 命名转换逐行进行，保留 Unicode 字母/数字（包括 Han），按分隔符、大小写转折、缩写尾部和数字/字母转折切词；没有可切分词项时保留原行。不是自然语言分词或拼音转换。
- 四种清理：逐行 trim、删除纯空白行、合并连续 ASCII 空格/Tab、删除 ASCII 空格/Tab。后两种保留换行及其他 Unicode 空白，不将所有空白都删掉。
- 六种行操作：字典升序/降序、自然数字排序、去重、反转、编号。字典排序使用字符串序，不提供语言区域排序。
- 自然排序识别 ASCII 数字片段，先比较去前导零后的长度，再逐位比较，不转换为 Long，因此长数字不会溢出。数字值相等、前导零或忽略大小写后相等时保留原次序；不按带符号数、小数或版本号的专门规则排序。
- 去重按完整原行区分大小写，保留首次出现。空行参与排序、去重、反转和编号。
- 逐行操作识别 CRLF/CR/LF，输出统一为 LF；末尾空行也保留并参与处理，除非操作本身删除空行。非逐行的整体大小写和空格处理保留原换行。

### 查找替换

- 普通模式对查找文本转义，替换文本按原文追加；`$` 与反斜杠不展开。
- 正则模式支持 RE2/J 常用字符类、分组、交替、量词和锚点。提供区分大小写、多行锚点和点号匹配换行选项；模式内标志按 RE2/J 规则解释。
- **不支持模式中的前后查找与反向引用。** 替换模板的 `$1` 是输出分组展开，含义与模式反向引用不同。
- 正则模式默认开启“展开分组”，支持 `$0`、有效编号 `$1…`、`${name}`。多个数字按最长有效分组编号解释；例如只有一个分组时 `$12` 为第 1 组加字面量 `2`。
- 展开时反斜杠引用下一个字符：`\$` 表示字面量美元符号，`\\` 表示反斜杠；末尾孤立反斜杠、无效分组、未知名称、孤立 `$` 或 `$x` 返回模板错误。不会自动把 `\n` 转成换行；实际换行可直接在替换输入中填写。
- 模板在查找前验证，即使没有匹配也会报告无效模板。未参与匹配的可选分组展开为空字符串。关闭“展开分组”时替换文本全按原文使用。
- 查找内容为空时返回原输入、匹配数 0；非空正则可作用于空输入。清空输入不等于清空模式，匹配空输入的表达式仍可能得到结果。
- 零宽匹配后按完整码点推进，在输入末尾的零宽匹配处理一次；不拆分合法代理对。成功的空字符串结果与“尚未处理”分开显示，可复制或回填。

## 资源限制与取消边界

| 项目 | 当前限制/策略 |
| --- | --- |
| 主输入与完整输出 | 各最多 200,000 个 UTF-16 单元；每次输出追加前检查，超限整次失败，不返回截断文本 |
| 普通查找 | 最多 4,096 个 UTF-16 单元 |
| 正则模式 | 最多 512 个 UTF-16 单元；编辑框可保留最多 4,096 单元，启用正则后按执行上限报告错误，方便修改 |
| 替换文本 | 最多 8,192 个 UTF-16 单元 |
| 逐行操作 | 最多 20,000 行，分行时检查；纯统计不受此行数限制 |
| 正则编译前 | 括号嵌套最多 32；按模式长度和所有计数重复因子计算保守展开预算，最多 16,384 |
| 正则编译后 | programSize 最多 8,192，捕获分组最多 32 |
| 执行时间 | 每次 Repository 调用取得互斥锁后开始 2 秒协作预算；统计和转换/替换分别计时 |
| 结果预览 | 最多显示前 8,000 个 UTF-16 单元，边界不拆代理对；页面明确提示预览不完整，复制与回填使用完整输出 |

正则预检查刻意高估独立计数重复的乘积，因此某些实际可执行的模式也可能被拒绝；这是一项明确的资源限制。字符类、转义、Unicode 属性与 `\Q…\E` 按预检查规则跳过，最终合法性由引擎确认。

时间预算不是硬实时保证。自有循环、比较、输出追加和引擎输入读取进行协作取消/时间检查；模式编译、编码有效性检查及系统大小写转换等同步步骤可能先完成才到下一检查点。编译前预算限制其工作规模，不能宣称点击取消会中断每个内部指令。超时、复杂度、内存或输出上限都提供中英反馈与重试。

输入在执行前拒绝不完整 UTF-16 代理字符。UTF-8 统计基于已验证的有效文本，不静默替换损坏字符。输入布局和剪贴板仍由 Compose/Android 处理，接近上限时的低内存、IME 与剪贴板表现需设备验收。

## UI、多语言及目录登记

- 遵循 `docs/ANDROID_UI_SPEC.md` 的共享主题、卡片、间距、最大内容宽度和键盘 Insets；复用 `ToolboxLoadingState` / `ToolboxLoadingIndicator`。
- 默认跟随系统，中文为默认资源，英语 key 同步。处理错误仅显示资源文案，不显示原始异常。
- 结果使用等宽文本并可选择，结果较长时选择范围仅限预览；全文通过“复制结果”取得。
- 中文和英文 Compose Preview 定义已更新；未渲染或验收 Preview、TalkBack、字体缩放、深色模式或设备键盘布局。
- 已有 `V11__seed_text_tool.sql` 只登记目录元数据，本轮未修改。V4–V13 在实际数据库的执行仍待验证；没有新增服务端文本处理接口。

## 实际验证

| 检查 | 实际结果 | 能证明的范围 |
| --- | --- | --- |
| `:app:dependencies --configuration debugRuntimeClasspath` | 成功解析 RE2/J 1.8 | 依赖版本与坐标有效 |
| 首轮 `:app:assembleDebug :app:lintDebug` | 成功，2 分 29 秒 | 新引擎、Repository、ViewModel、Compose 和资源能编译打包 |
| 两轮离线 `:app:assembleDebug :app:lintDebug` | 均成功，分别 56 秒、58 秒；最终 Lint 0 错误、27 警告 | 依赖已缓存，最终标签与代码通过构建/Lint 错误门禁 |
| 文本源码及新增资源 Lint | 0 条问题 | 当前静态规则未发现相关问题；不代表运行时逻辑验收 |
| 中英文本资源 | 各 75 个 key，集合相同、无重复 | key 对齐，不等于翻译、布局或无障碍验收 |
| APK 许可证条目 | `assets/licenses/re2j-1.8.txt` 已确认存在 | RE2/J 许可证随 APK 打包 |
| 差异和行尾空白 | `git diff --check` 与文本源码/文档行尾检查通过 | 已跟踪差异和本轮指定文件无空白错误 |
| 本轮临时目录 | `.tmp/text-fix` 已清理 | 未保留本轮构建临时废件 |

本轮开始已有二维码修复报告记载的 25 条 Lint 警告；最终实际报告为 27 条，分布在依赖更新提示、其他页面资源、Exif、清单与排版等项，不将差值归因于文本工具，也不称为零警告。编译还提示现有 Clipboard API 已弃用，后续可单独按公共组件范围迁移。

产物位于 D 盘项目内：`android/app/build/outputs/apk/debug/app-debug.apk` 与 `android/app/build/reports/lint-results-debug.html`。JAVA_HOME 为 D 盘 Android Studio JBR；沿用的 Gradle 应用缓存仍在 `C:/Users/沧烁/.gradle`，未宣称本轮迁移了固定应用数据。

没有新增或运行单元测试、自动化回归、正则样例、真机/模拟器或数据库联调。Lint 的测试源模型/静态分析任务不等于运行测试；下面是待验收清单，不代表已通过。

## 待验收清单

1. 统计：空文本、CRLF/CR/LF、末尾空行、Han 扩展字符、emoji、组合字符、非断行空格与损坏代理字符。
2. 转换：系统语言改变时 Locale.ROOT 规则、大写扩展、Han/缩写/数字混合命名，以及纯标点和末尾空行。
3. 排序：超过 Long 范围的数字、前导零、大小写相等、重复与空行稳定性。
4. 正则：大小写、多行/dotAll、命名/可选分组、零宽与空输入、非法模板、原文替换、被拒绝的 Java 正则功能。
5. 预算：模式嵌套/计数展开、20,000 行、输入/输出上限，以及边界附近的取消、超时、错误恢复。
6. 状态：快速编辑、切换选项、取消/重试、旋转/重建、旧请求不覆盖新结果；合法空结果的复制和回填。
7. UI：超过预览长度的全文复制、中英文、深色、字体缩放、TalkBack、IME 与低内存设备。

后续按审查报告先处理 JSON 的严格解析及根字符串输出问题，再处理 URL、日期与剩余工具；文本设备验收仍为独立未完成工作。
