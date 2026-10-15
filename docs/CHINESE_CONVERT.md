# 简繁互转（chinese_convert）

`chinese_convert`，分类 TEXT，模式 LOCAL，排序 100。Android 当前实现位于 `feature/chineseconvert/`，采用 Compose → ViewModel → UseCase → Repository；转换使用随 APK 分发的 OpenCC 数据，不调用网络、JNI 或外部转换程序，不申请额外权限。输入与模式通过 SavedStateHandle 保存。本文记录 2026-10-15 工作区实现及本轮数据生成验证，不表示工具已经通过发布验收。

## 源码查阅与适配依据

查阅日期：**2026-10-15**。上游固定为 [BYVoid/OpenCC 1.1.9 的提交 556ed22496d650bd0b13b6c163be9814637970ae](https://github.com/BYVoid/OpenCC/tree/556ed22496d650bd0b13b6c163be9814637970ae)，许可证 Apache-2.0。

本轮 GitHub 原始文件和代码页的 web 抓取返回 cache miss；实际阅读的是前轮从该固定提交下载到 `temp/chinese-convert/reference/` 的具体源码、11 份原始字典和 8 份配置，并核对 `fetch-reference.py`、`source-manifest.json` 的固定提交 URL、字节数及 SHA-256。生成脚本独立固定 31 个源文件的字节数和哈希，不以调用者传入的来源清单或现有资产 manifest 作为可信哈希来源。以下链接均指向该固定提交，未使用 master 替代。

| 实际查阅的业务源码 | 采用的行为与适配差异 |
| --- | --- |
| [DictGroup.cpp](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/src/DictGroup.cpp) | `MatchPrefix` 顺序访问子字典，首个有匹配的字典立即返回；不是先合并全部字典再取全局最长匹配。该文件的 `MatchAllPrefixes` 会收集并按长度排序，但转换路径调用的是 `MatchPrefix`，不能混用两者的规则。Kotlin 保留有序字典组。 |
| [MaxMatchSegmentation.cpp](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/src/MaxMatchSegmentation.cpp) | 先以指定词组表进行最大前缀匹配；连续未命中的字符累计为一个分段，命中词组时输出独立分段。Kotlin 按 Unicode 码点推进未命中位置，以 UTF-16 长度索引匹配键。 |
| [Converter.cpp](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/src/Converter.cpp)、[ConversionChain.cpp](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/src/ConversionChain.cpp)、[Conversion.cpp](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/src/Conversion.cpp) | 顺序为初始分段 → 在各分段内逐级转换 → 拼接；后一级保留原始分段边界，不重新对整篇输出分词。转换调用 `GetDefault()`，未命中字符原样输出。Kotlin 保留此流程，另加资源预算、取消与统计。 |
| [TextDict.cpp](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/src/TextDict.cpp)、[DictEntry.cpp](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/src/DictEntry.cpp) | 文本字典排序后检查重复键；多候选在文本中用空格分隔。另实际查看缓存 1.1.9 发行包的 `include/opencc/DictEntry.hpp`：`MultiValueDictEntry::GetDefault()` 取 `Values().at(0)`。该发行包头文件不计入生成器的 31 个固定源文件校验。本工具只分发默认候选，不提供运行时候选查询。 |
| [data/CMakeLists.txt](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/CMakeLists.txt)、[merge.py](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/scripts/merge.py)、[common.py](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/scripts/common.py)、[reverse.py](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/scripts/reverse.py) | 台湾词组合并顺序为 IT → Name → Other，排序时重键由最后一个输入覆盖；当前快照三份输入间没有重键。反向字典展开**全部**正向候选，按输入键顺序追加来源键，不去重，再排序反向键。完成合并及反向后，才将各条目裁为首候选 TSV。生成器在内存实现这些规则，不调用上游脚本或 OpenCC；本轮另运行已校验的上游原脚本做了数据生成对照。 |
| [LICENSE](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/LICENSE) | 资产许可证为固定说明前缀加上游 LICENSE 原始字节，保留完整条款及转换说明。缓存来源清单记录 NOTICE 为 404；本轮没有重新联网核验这一缺失状态。未随 APK 引入 OpenCC C++ 库及其依赖。 |

实际使用的原始字典为 [STCharacters](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/dictionary/STCharacters.txt)、[STPhrases](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/dictionary/STPhrases.txt)、[TSCharacters](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/dictionary/TSCharacters.txt)、[TSPhrases](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/dictionary/TSPhrases.txt)、[TWVariants](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/dictionary/TWVariants.txt)、[TWVariantsRevPhrases](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/dictionary/TWVariantsRevPhrases.txt)、[HKVariants](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/dictionary/HKVariants.txt)、[HKVariantsRevPhrases](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/dictionary/HKVariantsRevPhrases.txt)、[TWPhrasesIT](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/dictionary/TWPhrasesIT.txt)、[TWPhrasesName](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/dictionary/TWPhrasesName.txt)、[TWPhrasesOther](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/dictionary/TWPhrasesOther.txt)。没有从已裁过候选的正向 TSV 倒推反向数据。

### 八份转换配置

下表来自实际读取的固定 JSON，与当前 `planFor` 逐项对照。`[A, B]` 表示有优先顺序的字典组，`→` 表示后续转换级；所有级均在最初分段内执行。

| 模式及原始配置 | 初始分段字典 | 转换链 |
| --- | --- | --- |
| [s2t](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/config/s2t.json)：简体 → 繁体 | STPhrases | [STPhrases, STCharacters] |
| [t2s](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/config/t2s.json)：繁体 → 简体 | TSPhrases | [TSPhrases, TSCharacters] |
| [s2tw](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/config/s2tw.json)：简体 → 台湾正体 | STPhrases | [STPhrases, STCharacters] → TWVariants |
| [tw2s](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/config/tw2s.json)：台湾正体 → 简体 | TSPhrases | [TWVariantsRevPhrases, TWVariantsRev] → [TSPhrases, TSCharacters] |
| [s2twp](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/config/s2twp.json)：简体 → 台湾用词 | STPhrases | [STPhrases, STCharacters] → TWPhrases → TWVariants |
| [tw2sp](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/config/tw2sp.json)：台湾用词 → 简体 | TSPhrases | [TWPhrasesRev, TWVariantsRevPhrases, TWVariantsRev] → [TSPhrases, TSCharacters] |
| [s2hk](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/config/s2hk.json)：简体 → 香港繁体 | STPhrases | [STPhrases, STCharacters] → HKVariants |
| [hk2s](https://github.com/BYVoid/OpenCC/blob/556ed22496d650bd0b13b6c163be9814637970ae/data/config/hk2s.json)：香港繁体 → 简体 | TSPhrases | [HKVariantsRevPhrases, HKVariantsRev] → [TSPhrases, TSCharacters] |

生成器同时核对这 8 份配置的原始哈希和解析后的链。配置用于开发校验，App 执行上述固定计划，不在运行时加载或接受任意 OpenCC 配置。

## 当前功能与语义

- 输入、粘贴或选择三个内置示例后，按选定模式自动转换；输入变化使用 150 ms 防抖。支持清空、取消与重试。
- 词组优先，再按字表回退；台湾正体模式调整字形，台湾用词模式增加地区词汇转换；香港模式使用香港字形表。不存在独立的香港用词、普通话/粤语翻译或日文新字体模式。
- 展示输入、输出的 Unicode **码点数**及“变化分段”数。变化分段是最初分段经整条转换链后与原文不同的片段数；连续未命中区间可能包含多个被改的字，因此不能把它当改字数或自然语言词数。
- 结果预览最多 12,000 个 UTF-16 单元；复制和系统 `text/plain` 分享使用完整结果。支持把完整结果填回输入并选择配对的反向模式，结果超过输入上限时该按钮不可用。
- 未命中词条的字符保留，不进行 trim、Unicode 规范化、空白折叠或换行转换。空输入不加载字典；页面显示引导，不输出伪造结果。
- 页面代码使用共享输入、分组、结果、加载组件与中英文资源；有处理中、已取消、错误/重试和成功状态，复制/分享异常有提示。深浅色、字体、无障碍和系统分享的实际表现待设备验证。

当前没有文件导入、文件保存、批量文件、转换差异高亮、自定义词典或候选选择。简繁与地区用词存在多对一关系，反向转换不能保证还原原文，专有名词仍需人工核对。

## 输入、资源、Unicode 与取消边界

| 项目 | 实现约束及实际语义 |
| --- | --- |
| 输入 | 最大 100,000 个 **UTF-16 单元**，不是 100,000 个码点；非 BMP 字符占两个单元。超限编辑取消旧任务、保留原输入并提示，不截断。UseCase/Repository 也有前置长度检查。 |
| 输出 | 最大 300,000 个 UTF-16 单元，分段各级转换及最终拼接均在追加前检查；超限返回错误，不交付半截结果。 |
| Unicode | 拒绝孤立高/低代理项；有效代理对按码点推进，未命中的扩展汉字、emoji、组合符等保留。预览不会截开代理对，但可能切开组合字符或 ZWJ 字素簇；码点统计也不是视觉字素计数。 |
| 换行 | 原样保留 CR、LF、CRLF 以及空格、TAB；转换引擎不通过文本流重新编码换行。官方 Windows CLI 的文本流行为见后面的差分设计，不能据此要求 Android 把 LF 改为 CRLF。 |
| U+0000 | Kotlin 有意保留 NUL 及其后内容。上游 C/C++ 路径的零结尾循环会在 NUL 处停止，这是明确的适配差异，不纳入要求逐字一致的官方 oracle 样本。 |
| 耗时 | 取得 UseCase 互斥锁后开始计算 3 秒预算，包含首次字典加载；150 ms 防抖及等锁时间不计入该预算。通过检查点协作停止，不能把它描述为硬实时截止。 |
| 并发与取消 | UseCase 在 `Dispatchers.Default` 上串行执行。字典读取及转换循环约每 256 步检查协程取消/预算，阶段结束也检查；同步流读取、索引排序或编码检查不能保证被立即中断。编辑、切换模式、取消均递增版本号并取消旧 Job，旧结果不能回写新输入。 |
| 字典与失败 | 按需加载并缓存各张完整表；核对表行数、唯一键、单行不超过 256 个 UTF-16 单元及固定 SHA-256 后才发布缓存。取消或失败不会缓存半张表，先前已验证的其他表可保留。读取/格式/校验失败显示通用转换错误并允许重试，原始异常不展示给用户。 |
| 内存 | 12 个 TSV 总计约 1.1 MB，运行时另有字符串、HashMap、键长索引、阶段结果和输出缓冲；不能把磁盘数据大小当作堆占用。没有实测低端设备峰值内存和首次加载耗时。 |

Repository 本身的可变缓存依赖调用方串行访问；当前由 UseCase 的 Mutex 实现。未来若绕过 UseCase 并发调用，应先解决缓存并发约束。

## 可复现数据生成与校验

入口为 `scripts/build_chinese_conversion_data.py`，Python **3.10+**，仅用标准库，无联网、无运行时依赖、无脚本临时文件。当前正式资产位于 `android/app/src/main/assets/opencc/`，许可证在相邻 `licenses/opencc.txt`。

| TSV | 条目 | 字节 |
| --- | ---: | ---: |
| STCharacters | 3,980 | 33,333 |
| STPhrases | 49,096 | 1,004,369 |
| TSCharacters | 4,113 | 34,378 |
| TSPhrases | 278 | 5,141 |
| TWVariants | 39 | 312 |
| TWVariantsRev | 39 | 312 |
| TWVariantsRevPhrases | 68 | 1,054 |
| TWPhrases | 512 | 10,185 |
| TWPhrasesRev | 522 | 10,280 |
| HKVariants | 63 | 504 |
| HKVariantsRev | 70 | 560 |
| HKVariantsRevPhrases | 156 | 2,688 |
| **合计** | **58,936** | **1,103,116** |

每个 TSV 为严格 UTF-8、无 BOM、LF 行尾并含最后一个 LF，逐行 `键<TAB>首候选`，按 Unicode 码点顺序排序，不含重复键、空键/候选、额外列、控制字符或候选空格。生成器检查与 Repository 一致的 256 UTF-16 单元行长边界；当前最长实际行是 33 个单元。完整源候选在反向处理结束前保留，避免丢失如 `HKVariants` 的 `衹`、`糭` 反向键：63 条正向记录对应 70 个反向键，台湾用词 512 条对应 522 个反向键。

合并时按固定输入顺序覆盖重键，再排序；反向候选顺序来自完整正向表的源键顺序，不按候选词义或常用程度重排。例如 `TWPhrasesRev` 中 `儲存` 的候选顺序为 `保存 存儲`，打包默认值为 `保存`。这是上游默认选择规则，不能声称反向转换无损。

`manifest.json` 由脚本固定来源与目标规格生成，JSON 键排序、两格缩进、末尾 LF。现有 manifest 只收录 11 份原始字典的 `sourceSha256`；8 份配置、生成脚本和审阅源码的额外哈希固定在 Python 中，不擅自扩展现有 manifest 格式。所有源文件按原始字节校验，不规范化行尾；例如 `src/DictEntry.cpp` 本来没有末尾 LF。

- manifest：3,029 字节，SHA-256 `6d32ff78951668d89fb63d3d94a80fa1abbfc2071d27cc9b0a715d9a432728d4`。
- 许可证：9,396 字节，SHA-256 `14a7601eae4ce7606e255ab23287ea2ad2a1c8ee418fcc1e5a5ef094b54948be`；其中上游 LICENSE 原文 9,165 字节，SHA-256 `b534e465949558eec2597b04f5092b5e161236a68dfbfd04d547592ac3964308`。
- 12 份 TSV 的逐表哈希独立固定在脚本 `TABLES` 中，并与 manifest 一致；不能同时修改 TSV 和 manifest 就绕过校验。

### 运行方式

在项目目录使用已有的固定提交缓存；重新取得来源时，应按脚本 `SOURCE_FILES` 中的 31 个相对路径取固定提交的原始文件，完整 Git checkout 也需避免自动转换 CRLF。仅有 11 份字典、只有发行版 `.ocd2` 或可变分支的同名文件不能通过来源完整性检查。

```powershell
Set-Location 'D:\codeStudy\code\cangshuo-tool'
$env:PYTHONDONTWRITEBYTECODE = '1'
$env:TEMP = Join-Path (Get-Location).Path 'temp'
$env:TMP = $env:TEMP
$env:TMPDIR = $env:TEMP

# 无源文件也能只读核对现有资产：格式、数量、字节、固定哈希、来源元数据与许可
python -B scripts/build_chinese_conversion_data.py --check

# 核验 31 个源文件和 8 份配置，内存重建，再逐字节核对正式资产；不写入
python -B scripts/build_chinese_conversion_data.py temp/chinese-convert/reference --check

# 独立重建到项目 temp 下，生成 opencc/ 与 licenses/ 共 14 个文件
python -B scripts/build_chinese_conversion_data.py temp/chinese-convert/reference --assets-root temp/chinese-convert/rebuilt-assets
python -B scripts/build_chinese_conversion_data.py temp/chinese-convert/reference --assets-root temp/chinese-convert/rebuilt-assets --check
```

不提供源码的 `--check` 只能证明资产及来源声明与独立固定规格一致，**不能声称本次重新校验了上游源文件或重推导了字典**；输出会明确区分。带源码时才执行来源哈希、配置计划及完整重建校验。成功返回 0，校验或 IO 失败返回 1，参数错误返回 2。

默认生成目的地是正式 assets；本轮只在临时目录执行写入生成，对正式资产仅执行 `--check`。脚本先在内存完成全部生成和固定规格校验再写入，相同文件不重写；Windows 写入目的地必须在 D 盘，且会解析路径并拒绝逃出资产根目录的链接以及 opencc 目录中的额外文件。`--check` 不创建缺失目录，也不写文件。输出不含当前时间、机器路径或非确定性数据。

## 实际验证（2026-10-15）

### 本轮已执行的数据校验

2026-10-15，Windows，Python **3.13.12**：

1. 纯资产 `--check` 成功，退出码 0；验证现有 12 TSV、manifest 和许可证。
2. `temp/chinese-convert/reference --check` 成功，退出码 0；31 个源文件原始哈希、8 条配置计划及全部 14 个重建文件与现有资产逐字节一致。
3. CLI 在项目 `temp/chinese-data-cli-*` 下生成 14 文件；第二次生成报告 **0 文件写入**；针对生成副本的带源 `--check` 成功，三个进程均退出 0。
4. 临时隔离夹具 **50/50 项检查通过**：包含严格 UTF-8/孤立代理项字节、BOM/CRLF/缺末行 LF、空列/额外 TAB、重复/乱序键、控制符、Unicode 行分隔符、非 BMP 排序、256 单元行长边界、全候选反向及重复候选顺序、源字典/配置/脚本/CMake/源码/许可篡改、缺配置、读取预算、资产截断/同长篡改、资产与 manifest 同时伪造、额外字典、只读检查的字节/mtime、不创建失败目录与拒绝 C 盘输出。
5. 在上述夹具中执行已固定哈希的上游 `merge.py`、`reverse.py`，得到的 `TWPhrases`、`TWPhrasesRev`、`TWVariantsRev`、`HKVariantsRev` 经首候选输出后与本工具生成结果一致。

临时夹具及其生成物已清理；这 50 项是生成器专项运行记录，不是入库的 Android 测试套件。正式数据与缓存来源未修改，所有受控临时文件均在项目 `temp/` 下。

### 官方 OpenCC 差分语料与 Android 门禁

`scripts/build_chinese_conversion_fixture.py` 生成 `android/app/src/test/resources/chineseconvert/opencc-1.1.9.tsv`，由 `ChineseConversionRepositoryTest` 对实际打包字库执行比较。

- 独立 oracle 为官方 **OpenCC 1.1.9 CLI** 及其配置，生成器检查 CLI 版本；App 和已生成语料的测试执行不依赖外部可执行文件。
- 语料规模为 **952 组，8 个模式各 119 组**：固定词组、地区用词、标点/空白/Unicode 示例，加上每张实际字典首尾在内的均匀抽样并去重。每行使用 `模式<TAB>base64(UTF-8 输入)<TAB>base64(UTF-8 输出)`，空输入也保留。
- Windows CLI 文本流会将 LF 写成 CRLF。夹具生成器先拆出各行内容，通过官方 CLI 转换后按输入的原始 CRLF/CR/LF 分隔符重组；字典键不含换行，避免把命令行传输层改写误判为字典或引擎差异。该语料验证的是转换内容及应用要求的原分隔保留，不宣称复制 Windows CLI 的原始文件输出字节。
- Repository 测试读取真实打包表，按模式逐项比较语料输出与码点统计；另有直接测试覆盖 CRLF、非 BMP、组合符、NUL 的原样保留，以及空输入、非法 Unicode、输入限额、加载失败重试、取消、时间预算和变化分段语义。
- **NUL 的 C 接口截断是有意差异**，由 Kotlin 的直接保留测试验收，不要求用该 C 接口的 NUL 输出作相等 oracle。

Android 全量 **541/541** 通过，无失败、错误或跳过；本工具 **28 项 JUnit**（18 项 Repository、10 项 ViewModel），其中 8 项遍历并通过 **952 组差分样例**。涵盖快速编辑防抖、保留超限编辑前输入、真实 SavedStateHandle 重建、完整结果反向转换、取消/重试、数据损坏、Unicode 与精确输入上限。`assembleDebug`、`lintDebug` 通过，Lint **0 错误 / 81 警告**，本工具文件无发现。952 是语料条数，不是 JUnit 测试数，也不代表完整字典或所有上下文的穷举。

### 后端与后台联动

- 服务端完整 **226/226** 测试及候选镜像构建通过。候选镜像 `sha256:b7a04ed8d00a166a2844b48340523317d74c43fa9daa5698af13dc839ad3e068`。
- `scripts/check_chinese_convert_runtime.py --static` **71/71**，核对 V57、双语资源、字库与完整许可证；CI 同时加入资产固定哈希和该契约检查。
- 隔离真实 MySQL + API 候选 **450/450**：V57 迁移、公开目录、后台查询/创建/编辑及校验、匿名/普通账号权限、停用/维护/恢复、软删除、种子重放不覆盖人工配置、每日/累计使用量、热门榜及 7/30/90 天统计均通过。临时容器、数据库及用户已清理；未向生产写入合成使用记录。
- 生产发布：实现提交 `32e9024` 已在生产仓库同步，API 镜像 `sha256:b7a04ed8d00a166a2844b48340523317d74c43fa9daa5698af13dc839ad3e068` 已推广（回滚标签 `before-chinese-v57`），Flyway **57/57、失败 0**，线上 HTTPS 只读验收 **119/119** 通过；候选目录与冗余标签已清理。模拟器与真机交互范围见 [验收进度](CHINESE_CONVERT_ACCEPTANCE_2026-10-15.md)。

## 已确认的差异、限制与后续验收

- **数据生成未发现差异**：指定提交的源文本可精确重建当前全部 12 TSV、manifest 和许可证，无需调整现有资产。
- **算法保留的规则**：字典组按优先顺序短路，单字典取最大前缀，后续转换保持最初分段，输出默认候选。若未来改成跨字典全局最长匹配、把输出拼起来重新分词，或先裁候选再反向，会改变行为。
- **有意差异**：Kotlin 保留 U+0000 与后续内容并拒绝非法 UTF-16；增加输入/输出/时间限制和协作取消。官方 C/C++ 的 NUL 结束行为及 Windows CLI 文本流换行改写不作为 Android 目标行为。
- 数据固定于 1.1.9，不具备完整上下文语义理解；首候选、词表未覆盖的专名及一对多字义仍可能不符合用户意图。只支持本文 8 个模式，不能等同于 OpenCC 全部配置或字典 API。
- 真机、低端硬件加载耗时/峰值内存、真实进程死亡恢复、TalkBack 及最大结果复制/分享尚待验收；模拟器与生产的已验证范围以验收进度为准。

新 APK 安装后才会拥有本工具的原生实现；仅更新服务端不会升级用户设备。
