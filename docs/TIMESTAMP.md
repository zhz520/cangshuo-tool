# 时间戳转换工具

唯一编码 `timestamp`，分类 `DEV`，模式 `LOCAL`，排序值 110。`TimestampToolDefinition` 注册到 ToolRegistry，首页、目录、搜索与推荐共用同一份定义；无需账号、网络或 Android 权限。服务端目录登记由 V5 迁移完成，本轮没有修改迁移、接口或依赖。

2026-10-04 本轮修复：日期时间解析从默认 SMART 改为 `ResolverStyle.STRICT` + `uuuu` 模式，`2026-02-30` 不再被静默改写为 `2026-02-28`；新增“格式正确但日期/时间不存在”的独立错误与中英提示、1900–3000 年份范围校验、夏令时跳变时段拒绝；失败分类通过宽松回退解析区分，不改变用户输入与实际换算结果。

## 实际开源参考

查阅日期：2026-10-04。以下链接为当日取得的分支文件，不是固定提交；没有把项目名称或 README 当作完整业务源码参考。

| 来源与实际阅读范围 | 采用思路 | Android 适配与差异 |
| --- | --- | --- |
| [IT-Tools `date-time-converter.vue`](https://github.com/CorentinTh/it-tools/blob/main/src/tools/date-time-converter/date-time-converter.vue)（sha `5636ed4620f24600249218c712d30c2a18ba00ef`）：完整组件与格式列表、验证规则 | 无效输入必须显示错误而不是回退为空值；格式匹配后再转换的交互流程 | 参考其“无效输入可见错误”的策略；不引入 date-fns，转换使用 `java.time`；不实现 Excel、Mongo ObjectID、RFC 7231 等格式，只保留本项目已承诺的 7 种输入形状 |
| [CyberChef `FromUNIXTimestamp.mjs`](https://github.com/gchq/CyberChef/blob/master/src/core/operations/FromUNIXTimestamp.mjs)（sha `50d8539ea8a601c2931482f5ee4b28134ce9a5a4`）与 [`ToUNIXTimestamp.mjs`](https://github.com/gchq/CyberChef/blob/master/src/core/operations/ToUNIXTimestamp.mjs)（sha `3eaf2ba2717fe38403a553255bc06d40279ace9e`）：完整 run 与单位选项 | 秒/毫秒显式选择、UTC/时区输出、解析结果回显 | 参考单位切换与秒/毫秒双输出；未实现微秒/纳秒，未引入 moment-timezone 依赖 |
| [OpenJDK `ResolverStyle.java`](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/time/format/ResolverStyle.java)（sha `313bd178d846c4b2338a176579a76e65a1efa363`）：STRICT/SMART/LENIENT 说明 | STRICT 拒绝无效日期；SMART 会把超出当月天数的值改成当月最后一天（正是本轮缺陷）；LENIENT 允许月、时、分越界并按规则平移 | 转换路径只使用 STRICT；LENIENT 仅在一次“是否可识别输入形状”的判定中使用，不参与结果计算 |
| [OpenJDK `DateTimeFormatter.java`](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/time/format/DateTimeFormatter.java)（sha `9368cf54afde324497a0205bfc153562623ba968`，实际取得 700–860 行的 ISO 工厂与解析器说明） | 内置 ISO 解析器使用 STRICT；自定义模式在 STRICT 下应使用 `u`（proleptic year），`y`（year-of-era）缺少纪元信息会失败 | 自定义模式全部改为 `uuuu`；保留 `ISO_ZONED_DATE_TIME` 兼容偏移输入，年份范围与缺口检查仍由本工具执行 |

说明：IT-Tools 的 `timestamp-converter` 路径当日返回 404，实际取得并参考的是 `date-time-converter.vue`；没有复制第三方业务代码，也没有新增第三方许可证资产。

## 实现分层

```text
TimestampRoute / TimestampScreen
  → TimestampViewModel（SavedStateHandle、每秒刷新与暂停）
  → ConvertTimestampUseCase / ConvertDateTimeUseCase / GetCurrentTimeUseCase
  → TimestampRepository / LocalTimestampRepository
  → java.time.* + Strict/Lenient 解析器对
```

转换全部在设备本地运行，不发网络请求、不记录输入。日期输入上限 64 个 UTF-16 单元，解析与换算开销固定且很小，保持在 ViewModel 状态流中同步执行，不引入后台线程与取消机制。

## 功能覆盖与规则

### 当前时间与时间戳

- 每秒刷新系统默认时区时间（`uuuu-MM-dd HH:mm:ss`）；可暂停与手动刷新。
- 秒时间戳来自 `Instant.epochSecond`，毫秒时间戳来自 `toEpochMilli()`，两者可复制。
- 刷新使用系统时钟；设备时间或时区不正确时结果随系统，本工具不校验系统时钟。

### 时间戳转日期时间

- 单位显式选择秒或毫秒，不按位数自动猜测，避免把 10 位数字误当毫秒。
- 时区：系统默认、UTC、Asia/Shanghai、Asia/Tokyo、Europe/London、America/New_York、America/Los_Angeles；夏令时由 `ZoneId` 规则处理。
- 年份范围限定 1900–3000（秒级 epoch 约 `-2208988800` 至 `32503680000`），超出显示错误，不生成巨大或负数年份结果。
- 输出标准格式与 ISO 8601 偏移格式，两者均可复制。

### 日期时间转时间戳（本轮重点）

支持的输入形状（严格解析，不自动纠正）：

| 输入形状 | 说明 |
| --- | --- |
| `uuuu-MM-dd HH:mm:ss` | 标准日期时间 |
| `uuuu/MM/dd HH:mm:ss` | 斜杠分隔日期时间 |
| `uuuu-MM-dd'T'HH:mm:ss` | ISO 风格但没有偏移 |
| `uuuu-MM-dd HH:mm`、`uuuu/MM/dd HH:mm` | 缺省秒，按 0 秒处理 |
| `uuuu-MM-dd`、`uuuu/MM/dd` | 日期-only，按所选时区当天 00:00:00 处理 |
| ISO 8601 带偏移或 `Z` | 例如 `2026-02-28T12:00:00+08:00`；带偏移时以输入偏移为准，时区下拉只作用于无偏移输入 |

规则：

- 使用 `ResolverStyle.STRICT`。`2026-02-30`、`2025-02-29`、`1900-02-29`、`2026-13-01`、`24:00:00`、`12:60:00` 都返回错误，不自动改成相邻有效值。
- 闰年按公历：2024-02-29 与 2000-02-29 合法；2025-02-29 与 1900-02-29 非法（1900 不是闰年）。
- 年份必须落在 1900–3000；超出返回“超出支持范围”。
- 夏令时春季跳变缺口（例如 America/New_York 2026-03-08 02:30）不存在，返回“日期或时间不存在”，不自动后移。
- 夏令时秋季重叠时间（例如 America/New_York 2026-11-01 01:30）取较早偏移（EDT，-04:00），与 `ZonedDateTime.atZone` 的默认选择一致，并在文档中明确。
- 错误分三类：格式不支持（`InvalidFormat`）、格式正确但日期/时间不存在（`InvalidDate`）、超出年份范围（`OutOfRange`），界面分别显示对应中英文案。
- 空输入为等待状态，不是错误；超过 64 个 UTF-16 单元的新输入被拒绝并保留原值。
- 输入不会回写或格式化，转换只看解析结果与所选时区。

### 明确不支持

- 不解析 12 小时制、上午/下午、英文月份、中文日期、季度、周数或 `23:59:60` 闰秒。
- 不自动识别秒/毫秒/微秒/纳秒，也不实现微秒与纳秒换算。
- 不带偏移的输入一律按所选时区解释，不根据文本内容自动切换时区。
- 不做批量转换、历史记录、收藏参数或文件导入导出。
- ISO 区域后缀（如 `[Asia/Shanghai]`）由 `java.time` 解析；其缺口处理遵循库规则，本工具未额外拦截。

## 界面与交互

- 三张卡片分别承载当前时间、时间戳转日期时间、日期时间转时间戳。
- 最大内容宽度 600dp，适配键盘 Insets；结果使用等宽字体并可选择、复制。
- 日期输入框在错误时高亮，并按 `TimestampDateError` 显示“格式无效”“日期或时间不存在”“超出支持范围”之一，不再把不存在的日期描述成格式问题。
- 所有用户可见文案走中英资源，共 30 个 `timestamp_*` key。

## 服务端目录登记

`V5__seed_timestamp_tool.sql` 保持不变：code `timestamp`、分类 `DEV`、模式 `LOCAL`、排序 110、`ENABLED`、推荐位；关键词覆盖时间戳、timestamp、unix、epoch、时区等。本轮没有新增迁移、API、依赖或权限。

## 验证与验收

2026-10-04 验证记录：

| 检查 | 结果 | 能证明的范围 |
| --- | --- | --- |
| 离线 `:app:assembleDebug :app:lintDebug` | BUILD SUCCESSFUL，1 分 26 秒 | 源码可编译并通过 Lint 错误门禁；0 错误、24 条警告，数量与修复前一致，时间戳源码与新增资源无 Lint 问题；唯一编译提示为项目既有的 `LocalClipboardManager` 弃用告警 |
| 中英资源 key | `timestamp_*` 各 30 个，集合一致、无重复、全部被引用 | 英语切换不会因缺 key 回退中文 |
| JBR 行为验证（Android Studio 自带 JDK，临时程序已删除） | STRICT + `uuuu` 正确拒绝 `2026-02-30`、`2025-02-29`、`1900-02-29`、13 月、24 时、60 分；SMART + `yyyy` 复现 `2026-02-30 → 2026-02-28`；LENIENT 对上述形状均可识别；2024/2000-02-29 合法；New York 2026-03-08 02:30 缺口 offsets 为空、2026-11-01 01:30 重叠 offsets 为 `[-04:00, -05:00]` 且取较早偏移 | 验证了解析策略、闰年、缺口与重叠的实际 `java.time` 行为 |
| `git diff --check` | 通过 | 已跟踪差异没有空白错误 |

未执行：单元/仪器测试、业务样例的 Android 运行时执行、Preview 渲染、真机或模拟器、英语系统切换与剪贴板交互。构建与 JBR 行为验证不等于设备功能验收。

建议人工样例（待设备执行）：

1. `2026-02-30 12:00:00` → 显示“日期或时间不存在”；`2024-02-29` 成功；`2025-02-29` 与 `1900-02-29` 报错；`2000-02-29` 成功。
2. `2026-04-31`、`2026-13-01`、`2026-02-28 24:00:00`、`2026-02-28 12:60:00` → 均报错，不产生结果。
3. America/New_York 的 `2026-03-08 02:30:00` → 报错（夏令时缺口）；`2026-11-01 01:30:00` → 按 -04:00 计算。
4. `1800-01-01`、`3001-01-01` → 显示超出支持范围。
5. `2026/02/28 12:00`、`2026-02-28T12:00:00`、`2026-02-28`、`2026-02-28T12:00:00+08:00` → 均成功，秒/毫秒结果与预期一致。
6. 时间戳 `1728000000`（秒）与 `1728000000000`（毫秒）在七个时区切换后显示一致。

剩余风险：年份 1900–3000 是产品限制，不是 `java.time` 限制；夏令时缺口与重叠依赖设备 tzdb，系统更新时区数据库后行为可能变化；本轮未建立自动化回归测试。
