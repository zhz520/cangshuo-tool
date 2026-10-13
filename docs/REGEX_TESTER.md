# 正则表达式（regex_tester）

代码 `regex_tester`，分类 DEV，模式 LOCAL，排序 640，中文名「正则表达式」。RE2/J 1.8 在设备本地执行；离线可用、无权限、无网络、无上传。页面位于 `feature/regextester/`（Compose → ViewModel → UseCase → Repository），`RegexTesterToolDefinition` 注册到 ToolRegistry。服务端 V55 只登记目录元数据，解析在客户端完成，不新增执行接口。

## 源码查阅与适配

实际查阅日期：2026-10-13。

| 实际阅读的源码 | 采用的思路与差异 |
| --- | --- |
| [google/re2j 的 Matcher.java](https://github.com/google/re2j/blob/re2j-1.8/java/com/google/re2j/Matcher.java)、[Pattern.java](https://github.com/google/re2j/blob/re2j-1.8/java/com/google/re2j/Pattern.java)、[PatternSyntaxException.java](https://github.com/google/re2j/blob/re2j-1.8/java/com/google/re2j/PatternSyntaxException.java)（BSD-3-Clause，固定 tag `re2j-1.8`） | 核对 `find()` 对零宽匹配的前进规则（前进一个字符，本项目按 UTF-16 码点推进避免拆开代理对）、`group/start/end` 与未参与分组的 null 语义、`namedGroups()`、`programSize()/groupCount()` 预算接口，以及 `$n`/`${name}` 替换展开与转义规则。复用项目既有 `TextReplacementTemplate`、`RegexBudgetPolicy`、`TextWorkGuard`（文本工具已落地并记录在 [TEXT_TOOLS.md](TEXT_TOOLS.md)），本轮不修改这些共享组件。RE2/J 为已有依赖，许可证随 APK 打包在 `assets/licenses/re2j-1.8.txt`。 |
| [CorentinTh/it-tools 的 regex-tester.vue](https://github.com/CorentinTh/it-tools/blob/main/src/tools/regex-tester/regex-tester.vue) 与 [regex-tester.service.ts](https://github.com/CorentinTh/it-tools/blob/main/src/tools/regex-tester/regex-tester.service.ts)（MIT，main 分支，可变链接） | 参考「表达式 + 待匹配文本 + g/i/m/s 选项 + 结果表（位置、值、捕获、命名分组）」的交互组织与遍历中止条件（空匹配或未推进时停止）。Android 侧使用 RE2/J 而不是浏览器 JS 引擎；不引入 randexp 样例生成、regexper/NFA 图形化和 Unicode Sets 选项。未复制其代码。 |

## 功能

- 匹配模式：默认「全部匹配」，关闭后只取第一处；显示匹配数量、每处 UTF-16 `[start, end)` 位置、完整值，以及「整个文本完全匹配」判断。
- 捕获分组：编号分组与命名分组（`(?P<name>...)`）都显示索引、名称与范围；未参与分组显示为 null，空匹配（零宽）单独标注。
- 匹配选项：`g`（全部匹配）/ `i`（忽略大小写）/ `m`（多行锚点）/ `s`（点号匹配换行）；表达式不需要 `/` 分隔符，页面内说明 RE2 语法范围。
- 替换模式：预览替换结果。默认按字面文本替换，`$` 与反斜线原样保留；开启「展开捕获分组引用」后才解释 `$0`、`$1…`、`${name}`，转义规则与 RE2/J 一致。
- 常用示例：邮箱、数字、网址、日期（命名分组）、键值对、空白行；只填入表达式与示例文本，并在页脚说明示例不承担完整业务校验。
- 结果复制：复制全部匹配、单处匹配、单个分组、替换结果；复制失败有明确提示，不静默失败。
- 状态保持：表达式、文本、替换文本、模式与四个选项写入 SavedStateHandle，页面重建后恢复。

## 输入输出与资源边界

| 项目 | 限制 | 超限行为 |
| --- | --- | --- |
| 表达式 | 512 个 UTF-16 单元 | 保留原输入，提示简化表达式 |
| 待匹配文本 | 100,000 个 UTF-16 单元 | 保留原输入并显示当前用量，不静默截断 |
| 替换文本 | 8,192 个 UTF-16 单元 | 保留原输入 |
| 匹配数量 | 500 处 | 返回固定错误，不返回部分结果 |
| 捕获分组 | 32 个（沿用文本工具预算） | 判定为过于复杂 |
| 结果/替换输出 | 200,000 个 UTF-16 单元 | 返回固定错误，不返回部分结果 |

计算预算沿用文本工具的 2 秒工作预算、32 层嵌套、16,384 保守展开和 8,192 程序大小检查；表达式语法最终仍由 RE2/J 判定，预检查只用于预算保护。运行中可取消，新输入会使旧任务失效，旧结果不会覆盖新输入；取消不报错。

当前不支持：前后瞻、反向引用、占有量词等 Java/PCRE 语法（RE2 语义，页面已说明）；非法代理对会报 Unicode 错误；没有正则可视化、替换历史、批量文件处理和跨设备同步。

## 界面与多语言

- 复用 `ToolboxScaffold`、`ToolGroupCard`、`ToolboxTextField`、`ToolboxSwitch`、共享加载与主题令牌；匹配预览用主色浅底高亮前 4,000 个 UTF-16 单元，完整结果仍可复制。
- 中文与英语资源逐条对齐（含复数形式），示例与提示全部走 `strings.xml`；深浅色、1.5 倍字号与窄屏布局已实际检查。

## 前后端与验证

- V55 只新增目录 seed（DEV / LOCAL / ENABLED / `requires_login=0` / 排序 640），重放保留管理员编辑、维护状态与软删除；复用后台工具管理与 `/tools/regex_tester/usage` 统计。
- 隔离候选验收 **303/303**：中英文资源与契约字段、公开/管理读写、DISABLED 与 MAINTENANCE、软删除、热门列表、7/30/90 天统计；线上只读脚本 `scripts/verify_regex_tester_deploy.py` 不写生产用量。
- Android 全量单元测试 **475/475**（本工具新增 53 项：Repository 35 + ViewModel 18），`assembleDebug` 与 `lintDebug` 通过（0 错误；本工具文件 0 条 Lint 发现）。
- Android 16 模拟器实测：日期示例 2 处匹配、命名分组 year/month/day 展开、字面替换得到 `Start date, end date`、1.5 倍字号与深色正常渲染；截图 `regex-form.png`、`regex-groups.png`、`regex-replaced.png`、`regex-large-dark.png` 位于 `android/build/calc-emulator/`。
- 生产部署：API 镜像 `sha256:79518d05fd6e778f443fc040963e4d7c762bf1a8869cb70a2b9494edc60cd5b8` 上线，回滚标签 `before-regex-v55`，五个容器健康；线上 HTTPS 只读验收 **58/58** 通过（中英文契约、公开/管理目录一致、管理守卫、7/30/90 天统计、热门列表），未写入生产用量。进度与验收记录见 [REGEX_TESTER_ACCEPTANCE_2026-10-13.md](REGEX_TESTER_ACCEPTANCE_2026-10-13.md) 与 [路线图](../toolbox-vibe-spec/ROADMAP.md)。
