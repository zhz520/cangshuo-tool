# 随机密码生成

编码 `password_gen`，`TEXT / LOCAL`，排序 60。Compose → PasswordViewModel → GeneratePasswordsUseCase → PasswordRepository / LocalPasswordRepository；生成全部在本地完成，使用 `java.security.SecureRandom`，无网络、账号或权限要求。ToolRegistry 与 V47 工具目录使用相同名称、描述、图标与关键词。

## 开源参考与适配

实际源码查阅日期：2026-10-12。参考仓库 [brendanashworth/generate-password](https://github.com/brendanashworth/generate-password)（MIT，可变化的 master 分支，本次提交 `d11dddd0ea5dbb92c9ca7b960550813cabdde949`）：

| 来源 | 实际采用内容 | 适配差异 |
| --- | --- | --- |
| [src/generate.js](https://github.com/brendanashworth/generate-password/blob/d11dddd0ea5dbb92c9ca7b960550813cabdde949/src/generate.js)，blob `4303cbf84a809f68052857f7b3dd39c8ad80bf24` | 阅读字符集划分（小写 / 大写 / 数字 / 符号集共 30 个符号）、易混淆集合 `ilLI|` + `oO01`、`strict` 模式“每个启用类别至少一个字符”的规则、排除字符与批量生成的接口语义 | 未复制代码。`strict` 由“生成后校验、失败则递归重试”改为构造式：每类先取一个字符、补齐剩余长度、再 Fisher-Yates 洗牌，保证一次成型且位置随机；随机源使用 `SecureRandom.nextInt(bound)`（内部拒绝采样，无取模偏差）替代参考实现的 256 字节批量拒绝采样；不提供自定义符号串与生成回调 |

## 功能与边界

- 长度 4–64（默认 16）、数量 1–10；大写 / 小写 / 数字 / 符号四类字符集可分别开关（默认全开）。
- 选项：排除易混淆字符（与参考一致的 0O1lI 与反引号集合）、每类至少一个字符（默认开）、字符不重复、额外排除字符（最多 32 个，空白忽略）。
- 结果：等宽字体逐条展示（可长按选择），同时给出字符池大小、熵值（长度 × log2(池大小)）与强度标签（弱 < 40、中等 < 60、强 < 80、极强 ≥ 80 位）；支持「重新生成」与一键复制（多条按行分隔）。
- 边界处理：所有类别关闭或排除后池为空 → 明确提示；长度 / 数量非整数或越界 → 范围提示；「不重复」模式长度超过可用字符数 → 提示；超长排除输入拒绝采用并保留原值。
- 明确不支持：密码短语（passphrase / 词表）模式、自定义符号集、按站点保存历史、联网泄露检测（如 HIBP）；熵值是随机性上限估算，不代表任何具体攻击成本。

## 状态与界面

SavedStateHandle 保留全部选项；任一选项变化即时重新生成，「重新生成」通过 nonce 触发。错误与结果分区显示。复用共享 `ToolGroupCard`、`ToolboxTextField`、`ToolResultRow`、`ToolboxSwitch` 与主题；开关整行可点击并带 Switch 语义。中英资源齐全。

## 实际验证

2026-10-12：`PasswordGeneratorTest` 6 项覆盖字符池与易混淆排除、每类至少一个、额外排除与不重复模式、长度 / 数量 / 空池错误、熵值公式、批量生成；`PasswordViewModelTest` 3 项覆盖默认生成、范围错误恢复、开关切换与重新生成、超长粘贴保留原值。本批 Android 全量 **325/325** 测试通过（60 个套件），`assembleDebug` 与 `lintDebug` 通过（0 错误、83 警告；新增文件无 lint 告警）。

设备侧复制、键盘与大字号仍待验收；未做密码学审计，字符池与熵值仅为估算。
