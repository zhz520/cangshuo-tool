# 本地计算器

Phase 1 的首个实际工具，唯一编码 `calculator`，分类 `CALC`，模式 `LOCAL`。无需登录、网络或 Android 权限，默认启用并推荐，排序值为 10。应用入口将 `CalculatorToolDefinition` 注册到现有 `ToolRegistry`，首页、工具列表、计算分类及推荐区使用同一份定义。

## 实现分层

```text
CalculatorRoute / CalculatorScreen
  → CalculatorViewModel
  → CalculateExpressionUseCase
  → CalculatorRepository / LocalCalculatorRepository
  → 本地表达式解析器 / BigDecimal
```

计算逻辑不放在 Composable，不使用动态代码或 JavaScript 引擎。Repository 只执行长度受限的本地计算，不请求 API、不保存业务历史、不输出输入或结果日志。界面读取生命周期内的 StateFlow，通过资源显示错误。

## 运算规则

| 项目 | 当前支持 |
| --- | --- |
| 数字 | ASCII 数字，英文小数点；支持 `0.5`、`.5` 和 `5.` |
| 四则 | `+`、`-` / `−`、`*` / `×`、`/` / `÷`，遵循乘除优先与同级从左到右 |
| 括号 | 显式圆括号，嵌套最多 32 层 |
| 符号 | 一元正负号，例如 `-2`、`2 × -3` |
| 百分号 | 后缀 `%` 将前面的值除以 100；`200 + 10%` 为 `200.1`；界面中有说明 |
| 精度 | `MathContext.DECIMAL128`，34 位有效数字，HALF_EVEN 舍入；循环小数与超出精度的值会舍入 |
| 范围 | 算式最多 256 个字符，单个数值及格式化结果最多 128 个字符 |
| 输出 | 去除多余小数零，负零显示为 `0`，不使用千位分隔或科学记数 |

暂不包含科学函数、幂、单位、隐式乘法、科学记数、内存寄存器或计算历史。空输入、非法算式、零除、超长输入、嵌套过深和结果超范围使用固定的中英文错误提示，不展示异常原文。

## 页面与状态

算式可直接输入或粘贴，也可使用四列按键。按键插入、删除会尊重当前光标或选区；输入变化清除旧结果，避免把旧结果视为新算式结果。支持清空、计算、选择结果、复制和把结果放回输入继续计算。复制由用户操作触发，使用系统剪贴板；Android 13 及以上使用系统复制反馈，更早版本使用 Snackbar。

界面使用可滚动容器和 600dp 最大宽度，保留软键盘 Insets。`SavedStateHandle` 保存表达式与成功结果，ViewModel 用于配置变化及返回后再打开的状态保留；不建立最近使用或历史记录。进程重建后首页先返回目录，重新打开计算器时可恢复系统保存的界面状态。上述设备行为尚待运行验证。

内置名称和描述从当前应用 Resources 读取，身份、分类、状态、模式及排序保持固定。语言配置变化时首页刷新元数据，计算器界面文案从当前资源读取；语言规则见 [多语言说明](ANDROID_LOCALIZATION.md)。

## 服务端目录登记

新增 `V3__seed_calculator_tool.sql` 登记已经实现的计算器元数据，字段与 Android 定义对齐。使用既有 `CALC` 分类，主语言为中文，关键词同时包含中文和英语；已有同编码记录保留，不覆盖人工配置或删除状态。此迁移不增加表、不修改 V1/V2、不下发可执行代码，也不建立服务端计算接口。API 契约字段保持不变，Android 尚未执行远程目录合并。

## 在 Android Studio 查看

1. 打开 `android/`，执行 Gradle Sync。
2. 选择模拟器或真机运行 `app`，从首页或计算分类打开计算器。
3. 中英文预览位于 `CalculatorScreen.kt`；系统应用语言的使用方式见多语言说明。

## 验证记录

2026-10-04 使用现有 JDK 21.0.9、SDK 36 及工具链完成以下门禁：

- Android `:app:assembleDebug :app:lintDebug` 最终成功，用时 47 秒，45 个任务中 10 个执行、35 个使用已有产物；Debug APK 已生成。
- Lint 为 0 错误、11 警告：10 个既有 SDK/依赖版本与应用图标提示，1 个 `localeConfig` 仅在 API 33+ 生效的提示；旧系统按正常资源规则跟随系统语言。
- Server Windows Maven `package -Dmaven.test.skip=true` 成功；新 JAR 已恢复 local 预览，V3 执行成功，数据库版本为 3。
- 本地 Docker API 镜像构建成功并通过现有启动健康门禁，启动日志确认三份迁移校验通过、版本为 3、无需重复迁移。
- Git 差异空白检查通过；V1/V2 内容保持原样。

未新增或执行自动化测试，未运行计算样例或启动设备；运算边界、光标、复制、状态恢复和语言切换仍待运行验证。构建、资源静态检查与 API 启动门禁不表示计算器功能或设备联调已经验证。

## 参考

- [JDK 21 MathContext](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/MathContext.html)
- [Android 剪贴板与复制反馈](https://developer.android.com/develop/ui/compose/touch-input/copy-and-paste)
