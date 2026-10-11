# 进制转换

唯一编码 `radix`，分类 `CALC`，模式 `LOCAL`，排序值 25。`RadixToolDefinition` 注册到 ToolRegistry，首页、目录、搜索共用同一份定义；无需账号、网络或 Android 权限。服务端目录登记由 V41 迁移完成。

## 参考实现

查阅日期：2026-10-11。最初实现未阅读业务源码；本次修复已实际阅读 IT-Tools 的转换模型与页面，不把此前的项目名称记录视为源码研究。

| 来源 | 参考点 | 本项目差异 |
| --- | --- | --- |
| [IT-Tools 转换模型](https://github.com/CorentinTh/it-tools/blob/main/src/tools/integer-base-converter/integer-base-converter.model.ts)，本次内容 blob `da0fe77f8b71b8ab39d67dcda53f6a63353b33b1` | BigInt 位权累加、逐位校验和除基取余，零结果明确为 0 | 使用 Java BigInteger、不复制源代码；限定 2–36、大小写不敏感，并扩展正负号和前缀 |
| [IT-Tools 页面](https://github.com/CorentinTh/it-tools/blob/main/src/tools/integer-base-converter/integer-base-converter.vue)，main 可变化链接 | 数值/输入进制独立选项、并列常用结果/复制、异常反馈 | 原生 Compose、后台串行转换和最新输入归属、10,000 字符预算，不支持上游自定义 64 位数字表 |

CyberChef 的预期 ToRadix 源码路径返回 404，未列为本轮已阅读依据。

## 功能与边界

- 支持进制 2-36（数字 0-9、字母 a-z），输入大小写不敏感；输出字母大小写可切换。
- 自动识别 0x / 0b / 0o 前缀；显式进制与前缀匹配时保留前缀语义，冲突时按显式进制校验原文（例如十进制下输入 0x10 会报告第 2 个字符无效）。
- 任意精度：大数不受 64 位限制，输入长度上限 10,000 UTF-16 单元，在 UI 与 Repository 都于计算前校验。超长粘贴拒绝采用、保留旧输入并显示错误，不悄悄截断为有效结果。
- 分隔符（空格、制表符、换行、逗号、下划线、单引号）被忽略；非法字符报告原输入中 1-based UTF-16 位置，包含前置空白、符号与前缀。
- 支持前置 +/− 与负数前缀；位数/字节按绝对值幅度统计，不表示二进制补码存储。零的幅度为 0 bit / 0 字节；分数/科学计数法不支持。
- 修复 AUTO=-1 被直接传入 Repository 导致默认模式全部报错的问题：存储哨兵在 ViewModel 边界转为 null，自动选项恢复选中。大数转换在 Default dispatcher，transformLatest 隔离旧结果、串行等待有界原生计算结束；共享加载组件提供反馈。
- 输出 BIN/OCT/DEC/HEX/B36 五种结果，加位数、bit 长度、字节数统计；每个结果可单独复制或一键复制全部。

## 实现分层

RadixRoute / RadixScreen → RadixViewModel（SavedStateHandle 保留输入/进制/大小写）→ ConvertRadixUseCase → RadixRepository / LocalRadixRepository → java.math.BigInteger。

## 验证范围

本次修复新增 4 个算法边界和 4 个真实 ViewModel 回归，完整 Android 251/251 单测通过，assembleDebug/lintDebug 通过（2 分 18 秒）。进制选择改为 FlowRow 避免窄屏挤出，补跑 APK/Lint 通过（1 分 2 秒）；Lint 0 错误/69 警告，没有 radix 文件问题。没有联网接口变更，V41 元数据继续使用已有后台控制；真机/模拟器交互未验证。

2026-10-11：`RadixConversionTest` 10 个用例（常用进制互转、大小写输出、前缀识别、显式进制冲突、分隔符容错、非法字符位置、空输入、大数往返、36 进制往返）；Android 全量单测 243 项通过；`assembleDebug` 与 `lintDebug` 通过。
