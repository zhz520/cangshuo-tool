# 字数统计

编码 `text_counter`，`TEXT / LOCAL`，排序 65。Compose → TextCountViewModel → AnalyzeTextUseCase → TextCountRepository / LocalTextCountRepository；统计全部本地完成，无网络、账号或权限要求。ToolRegistry 与 V47 工具目录使用相同名称、描述、图标与关键词。

## 开源参考与适配

实际源码查阅日期：2026-10-12。参考仓库 [lepture/word-count](https://github.com/lepture/word-count)（仓库内无 LICENSE 文件，README 声明 MIT，作者 Hsiaoming Yang；本次提交 `d1af3f01e9eb6636d59f1305400f531cd888e793`）：

| 来源 | 实际采用内容 | 适配差异 |
| --- | --- | --- |
| [index.mjs](https://github.com/lepture/word-count/blob/d1af3f01e9eb6636d59f1305400f531cd888e793/index.mjs)，blob `fcd23cd39fee6ceb130084b6c14fd4efc6c6afd7` | 阅读“CJK 按字符计数、拉丁词按词计数”的混合口径（示例 `hello means 你好` = 4）与 CJK 区段划分（含扩展 A、兼容区） | 独立实现且按**码点**迭代（surrogate pair 计 1 个字符，emoji 不误算）；在参考的两类计数之外，增加不含空格字符数、行数 / 非空行 / 段落数、UTF-8 体积与阅读时间估算；行语义明确为 Java split 风格（结尾换行不产生空行），与参考只统计词数不同 |

## 功能与边界

- 输入上限 20 万字符，超长粘贴拒绝并保留原内容。
- 指标：字符数（含空格）、字符数（不含空格）、中文字符数（基本区 + 扩展 A + 兼容区 + 扩展 B 及以后）、英文单词数（字母/数字串，允许词内连字符、撇号与下划线）、字数（中文按字 + 英文按词）、行数、非空行、段落数（连续非空行视为一段）、UTF-8 体积（字节）、预计阅读时间（中文 300 字/分、英文 200 词/分，至少 1 秒）。
- 空白字符不计入“不含空格”；全空白输入显示引导而不显示结果。
- 明确不支持：分词（jieba 等）与词频统计、按语言自动切换阅读速度、文件导入；阅读时间只是估算。

## 状态与界面

SavedStateHandle 保留输入文本；文本为空显示引导，超长显示错误并保留旧值。结果卡复用 `ToolGroupCard` 与 `ToolResultRow`，可复制单行摘要；多行输入框（6–12 行）随键盘滚动。中英资源齐全。

## 实际验证

2026-10-12：`TextCountTest` 6 项覆盖混合文本口径、surrogate pair 与 emoji、行 / 段落语义（含结尾换行）、连字符与撇号单词、阅读时间（含 1 秒下限）、空文本；`TextCountViewModelTest` 2 项覆盖空输入引导与统计结果、超长粘贴保留原值。本批 Android 全量 **325/325** 测试通过（60 个套件），`assembleDebug` 与 `lintDebug` 通过（0 错误、83 警告；新增文件无 lint 告警）。

未连接真机或模拟器验收大段粘贴、键盘与大字号；阅读时间未与真实阅读实验校准。
