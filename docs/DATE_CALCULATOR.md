# 日期计算器

编码 `date_calc`，`CALC / LOCAL`，排序 50。Compose → DateCalcViewModel → CalculateDateUseCase → DateCalcRepository / LocalDateCalcRepository；日期差值与日期推算全部在本地执行，无网络、账号或权限要求。ToolRegistry 与 V45 工具目录使用相同名称、描述、图标与关键词，后台复用既有工具管理、开关和使用统计。

## 开源参考与适配

实际源码查阅日期：2026-10-12。date-fns 仓库树 `717ce0a807ea4c6b540d015b5c408723175b2838`（可变化的 main 分支，记录本次读取的 blob）：

| 来源 | 实际采用内容 | 适配差异 |
| --- | --- | --- |
| [differenceInCalendarDays/index.ts](https://github.com/date-fns/date-fns/blob/717ce0a807ea4c6b540d015b5c408723175b2838/pkgs/core/src/differenceInCalendarDays/index.ts)，blob `6f7702dc71bbdce61678ab59c5314483989a206c` | 阅读“去掉时刻、只按日历天求差”的语义及其对夏令时周取整的处理 | 本项目使用 `java.time.LocalDate`（纯日历日期，无时刻与时区），不需要时区偏移修正常规化；独立实现，未复制代码 |
| [addMonths/index.ts](https://github.com/date-fns/date-fns/blob/717ce0a807ea4c6b540d015b5c408723175b2838/pkgs/core/src/addMonths/index.ts)，blob `48ec06c3797018cb348c1d3eda339c5f780ea7d3` | 阅读月末收口规则：目标月天数不足时停在月末，且 0 个月时不动时间 | `LocalDate.plusMonths` / `plusYears` 原生采用同一收口语义，直接使用 JDK 行为；独立实现 |
| [differenceInBusinessDays/index.ts](https://github.com/date-fns/date-fns/blob/717ce0a807ea4c6b540d015b5c408723175b2838/pkgs/core/src/differenceInBusinessDays/index.ts)，blob `1a4366871adbf635e160a2df5230bc42fe9a0fbd` | 阅读“整周 × 5 + 至多 6 天余数循环”的算法与区间语义：统计 [较早, 较晚) 内的工作日 | 独立实现同一思路；固定周六、周日为周末，未提供自定义周末参数；不包含公共假日数据 |
| [LICENSE.md](https://github.com/date-fns/date-fns/blob/717ce0a807ea4c6b540d015b5c408723175b2838/pkgs/core/LICENSE.md)，blob `29c6e85fd3248d26520354a84ee70e961f3475d1` | MIT 许可确认，允许参考与改编 | 按项目约定仍为独立实现、未引入任何 JS 依赖，仅保留来源记录 |

## 功能与边界

- **日期差值**：任意两个日期顺序无关；输出总天数、周 + 天、年月日拆分、工作日与周末天数，可复制摘要。
- **年月日拆分**采用与本工具推算一致的月末收口规则：2024-02-29 → 2025-02-28 记为 1 年，2026-01-31 → 2026-02-28 记为 1 个月。若直接用 `java.time.Period.between`，上述两例分别得到 11 个月 30 天与 28 天，与“推算”结果不一致；因此实现整月探测 + 至多一次回退 + 余数天的拆分（见 `LocalDateCalcRepository.calendarBreakdown`）。
- **工作日**统计 [较早日期, 较晚日期) 内周一至周五的天数：不含结束日期，同一天或只跨周末为 0；仅排除周六周日，法定节假日与调休未计入（页面固定说明）。
- **日期推算**：基准日期 + 数量 0–100000 + 单位（天/周/月/年）+ 方向（之后/之前）；天与周为精确天数，月与年按日历推算并收口到月末（1 月 31 日加 1 个月 = 2 月 28 日，闰年为 2 月 29 日）；结果展示日期、星期与有符号移动天数。
- 输入格式固定为 `yyyy-M-d`，接受 `-` 或 `/` 分隔、四位年份（如 `2026-10-12`、`2026/4/1`），不随设备区域变化；非法格式、越界年份、非法数量、结果越界分别给出可操作的本地化提示。
- 日期范围 1900-01-01 至 2999-12-31（含推算结果）；超出范围提示而不是静默收口。
- 日期字段最多 12 字符、数量字段最多 6 字符；超长粘贴拒绝采用、保留原值并提示。
- 不支持：时区/时刻换算（使用时间戳工具）、法定节假日与调休日历、自定义周末、1900–2999 之外的日期、导出文件或历史记录。

## 状态与界面

SavedStateHandle 保留两段功能的全部字段与单位/方向；枚举损坏时回退 DAYS / AFTER。“今天”按钮从注入时钟取当天日期（测试固定 2026-10-12）。两段分别校验、分别显示错误或结果，空表单显示引导文案；计算为纯本地同步运算，无人工加载延迟。复用共享 `ToolGroupCard`、`ToolboxTextField`、`ToolResultRow`、`ToolboxTextButton` 与主题，中英资源齐全；窄屏与大字号下结果行由 `ToolResultRow` 自动改为上下排布。

## 实际验证

2026-10-12：`DateCalcTest` 6 项算法测试覆盖顺序无关差值、跨月拆分、月末收口（2026-01-31 → 2026-02-28 为 1 个月；2026-02-28 → 2026-03-31 为 1 个月 3 天）、闰日整年（2024-02-29 → 2025-02-28 为 1 年）、工作日与周末（含 2025 全年 261 个工作日）、天/周双向移动与 0 数量、1900/2999 结果边界；`DateCalcViewModelTest` 4 项回归覆盖空表单引导、非法格式与越界年份区分、超长粘贴保留原值、单位/方向切换、数量与结果越界、清空以及注入时钟的“今天”按钮。本批 Android 全量 **290/290** 测试通过（52 个套件），`assembleDebug` 与 `lintDebug` 通过（0 错误、77 警告；新增与修改文件无 lint 告警）。V45 目录与 Android 资源的名称、描述、关键词由 `scripts/check_calc_catalog_runtime.py` 的扩展检查在隔离数据库运行验收中核对（结果见路线图本批条目）。

本次没有连接真机或模拟器：键盘避让、大字号、旋转恢复与 TalkBack 仍需设备验收；1900–2999 边界之外的输入按设计直接提示，不参与计算。
