# 进制转换

唯一编码 `radix`，分类 `CALC`，模式 `LOCAL`，排序值 25。`RadixToolDefinition` 注册到 ToolRegistry，首页、目录、搜索共用同一份定义；无需账号、网络或 Android 权限。服务端目录登记由 V41 迁移完成。

## 参考实现

查阅日期：2026-10-11。本轮以标准数值算法实现（`java.math.BigInteger` 任意精度转换），产品形态参考以下开源工具的功能范围；本轮未逐行对照其源码，源码级对照留待后续迭代补充。

| 来源 | 参考点 | 本项目差异 |
| --- | --- | --- |
| [CyberChef](https://github.com/gchq/CyberChef) "From Radix"/"To Radix" 操作 | 任意进制、大小写开关、严格字符校验 | 单页并列输出 5 种常用进制，增加位长与字节数统计 |
| [IT-Tools](https://github.com/CorentinTh/it-tools) number-base-converter | 常用进制并列展示与复制交互 | 增加 0x/0b/0o 前缀自动识别与自定义进制滑杆 |

## 功能与边界

- 支持进制 2-36（数字 0-9、字母 a-z），输入大小写不敏感；输出字母大小写可切换。
- 自动识别 0x / 0b / 0o 前缀；显式进制与前缀匹配时保留前缀语义，冲突时按显式进制校验原文（例如十进制下输入 0x10 会报告第 2 个字符无效）。
- 任意精度：大数不受 64 位限制，输入长度上限 10,000 字符（UI 限制）。
- 分隔符（空格、制表符、逗号、下划线、单引号）被忽略；非法字符报告 1-based 位置。
- 输出 BIN/OCT/DEC/HEX/B36 五种结果，加位数、bit 长度、字节数统计；每个结果可单独复制或一键复制全部。

## 实现分层

RadixRoute / RadixScreen → RadixViewModel（SavedStateHandle 保留输入/进制/大小写）→ ConvertRadixUseCase → RadixRepository / LocalRadixRepository → java.math.BigInteger。

## 验证范围

2026-10-11：`RadixConversionTest` 10 个用例（常用进制互转、大小写输出、前缀识别、显式进制冲突、分隔符容错、非法字符位置、空输入、大数往返、36 进制往返）；Android 全量单测 243 项通过；`assembleDebug` 与 `lintDebug` 通过。
