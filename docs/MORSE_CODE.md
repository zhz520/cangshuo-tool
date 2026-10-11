# 摩斯电码

唯一编码 `morse`，分类 `TEXT`，模式 `LOCAL`，排序值 155。`MorseToolDefinition` 注册到 ToolRegistry；无需账号、网络或 Android 权限。服务端目录登记由 V42 迁移完成。

## 参考实现

查阅日期：2026-10-11。字母表采用 ITU-R M.1677 国际摩斯码（字母、数字、常用标点）。产品形态参考以下开源工具；本轮未逐行对照其源码，源码级对照留待后续迭代补充。

| 来源 | 参考点 | 本项目差异 |
| --- | --- | --- |
| [CyberChef](https://github.com/gchq/CyberChef) "To Morse Code"/"From Morse Code" | 双向转换与分隔符规则 | 增加标准时序音频播放与不支持字符清单 |
| [IT-Tools](https://github.com/CorentinTh/it-tools) morse-converter | 转换交互与错误提示 | 支持 5-30 WPM 调速播放，间隔按 PARIS 标准 |

## 功能与边界

- 双向转换：文本→摩斯（空格转为词分隔符）、摩斯→文本（容错多余空白与连续斜杠）。
- 字母表：A-Z、0-9 与 . , ? ' ! / ( ) & : ; = + - _ " @ $。
- 不支持字符（emoji、CJK 等）不替换为乱码：按码点统计并跳过，UI 列出前 20 个提示。
- 解码时无效符号报告 1-based 符号序号；空输入明确提示。
- 音频播放：700 Hz 正弦波，1 unit = 1200 / WPM 毫秒（PARIS 标准），5-30 WPM 可调；点 1 unit、划 3 unit、符号间 1 unit、字母间 3 unit、词间 7 unit；AudioTrack 流式写入，支持停止，结束自动释放。

## 实现分层

MorseRoute / MorseScreen → MorseViewModel（SavedStateHandle + 播放状态）→ EncodeMorseUseCase / DecodeMorseUseCase / BuildMorseTimelineUseCase → MorseRepository / LocalMorseRepository（MorseAlphabet 映射）→ MorseAudioPlayer（AudioTrack 流式正弦波）。

## 验证范围

2026-10-11：`MorseCodingTest` 8 个用例（字母/数字/标点编码、大小写、不支持字符清单、词分隔容错、无效符号位置、空输入、标准间隔时序、解码方向时序）；Android 全量单测 243 项通过；`assembleDebug` 与 `lintDebug` 通过。音频实际听感需真机复测。
