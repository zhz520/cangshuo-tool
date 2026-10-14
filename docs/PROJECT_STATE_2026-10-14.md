# 项目中断后的状态核查（2026-10-14）

本次按用户要求检查其他软件操作后留下的项目状态。核查期间保留原有源码和未提交改动，不提交、不推送、不部署；仅新增本记录。主线程负责 Git、构建报告、目录接线和生产状态；子智能体 Huygens 负责拼音工具业务代码、UI、测试与文档的只读复核。

## 结论

当前中断的是“汉字转拼音”工具（`pinyin`），已经有 Android 实现及服务端 V56 目录种子，但尚未达到交付状态。上一轮正则表达式及短视频解析修复的已提交成果仍在，生产运行正常。

## Git 与未提交内容

- 本地分支 `main`、GitHub `refs/heads/main` 与生产仓库均为 `9cc6a2a479f6c29b9c7128a6d893df34e17fdf5b`。
- 核查开始时为 **6 个已跟踪文件修改、12 个未跟踪文件**，没有已暂存内容或合并冲突。
- 6 个修改文件：`android/app/build.gradle.kts`、`ToolboxAppContainer.kt`、中英文 `strings.xml`、`android/gradle.properties`、`android/gradle/libs.versions.toml`。
- 12 个未跟踪文件：`feature/pinyin/` 下 9 个 Kotlin 源文件、`PinyinTest.kt`、`docs/PINYIN.md`、`V56__seed_pinyin_tool.sql`。
- 管理后台源码、服务端 Java 源码、CI 工作流没有这次未提交改动；后台设计稿 `docs/design/admin-ui-rewrite.html` 也没有这次修改。

## 已有实现

- Android 注册中心已接入 `PinyinToolDefinition`；Compose → ViewModel → UseCase → Repository 分层已有文件。
- 有完整拼音、首字母、多音字查询，以及声调、大小写、间隔符、ü 形式、非汉字保留选项。
- 已新增 `com.belerweb:pinyin4j:2.5.1` 依赖与中英文资源。
- V56 是目录登记种子（TEXT / LOCAL / sort 95），本地工具不需要新增服务端执行接口。后台已有通用目录管理，可在迁移上线后管理此工具，但本次尚未验证该联动。

## 实际检查结果

| 检查 | 结果与证据范围 |
| --- | --- |
| Android 单元测试 | 现存 XML 报告合计 **486 项、0 failures、0 errors**，包含 **11 项拼音测试**；报告时间为 2026-10-13 23:08 左右。本轮 Gradle 判断 `testDebugUnitTest` 为 `UP-TO-DATE`，未强制重新执行测试。 |
| Debug APK | 现存 `app-debug.apk` 为 2026-10-13 23:10 生成，大小 21,475,431 字节；本轮 `assembleDebug` 为 `UP-TO-DATE`。 |
| Android Lint | 本轮重新分析后 **失败：2 个错误、88 个警告**。报告时间为 2026-10-14 10:30；不能沿用旧报告声称全部门禁通过。 |
| Admin | 本轮 `npm run typecheck` 通过；未进行新的浏览器交互验收或生产构建。 |
| Git 格式检查 | `git diff --check` 通过。 |
| GitHub | 本轮 `git ls-remote` 只读确认仍为 `9cc6a2a`，这批拼音改动尚未推送。 |
| 生产仓库 | 只读 SSH 确认 HEAD 为 `9cc6a2a`，工作区干净。 |
| 生产容器 | server、admin、media-browser、mysql、redis 五个容器全部 `healthy`；API 容器内部健康接口返回 `UP`。 |
| 生产数据库 | Flyway 最高版本 **55**，55 条历史、失败记录 0；`regex_tester` 启用，`pinyin` 尚未登记。未执行 V56。 |
| 外网 | 公开目录 HTTP 200 / code 0 / 共 43 个工具，含正则、不含拼音；后台登录页 HTTP 200。只读访问，不更改用户数据。 |

构建报告位于 `android/app/build/reports/lint-results-debug.html`、`android/app/build/reports/lint-results-debug.xml`。单元测试 XML 位于 `android/app/build/test-results/testDebugUnitTest/`。

## 确认的收尾缺口

1. **构建门禁被 JDK 配置阻断。** `android/gradle.properties:10`、`:11` 新增的 `D:/Android/Android Studio/jbr` 路径触发 `PropertyEscape` 两个错误。将本机 JDK 路径写入共享工程配置，还会让使用 Ubuntu 的 CI 指向不存在的 D 盘路径（静态核查，未重新执行 GitHub CI）。应改为本机环境/私有配置，保持工程跨平台。
2. **双语资源不一致。** `pinyin_keywords` 中文为 7 项、英文为 6 项，Lint 实际报 `InconsistentArrays` 警告，不能称其为双语契约完全一致。
3. **图标接线缺失。** 元数据声明 `icon = "pinyin"`，但 `HomePresentation.kt` 的图标映射没有该项，也未新增专用图标；目前会落入通用工具图标。
4. **交付记录缺失。** ROADMAP 未记录拼音进行中，功能总清单仍标未实现。`docs/PINYIN.md` 声称测试、APK、Lint 全通过，与本轮实际 Lint 失败不符。隔离候选、后台管理与统计联动、设备验收、提交、部署、线上验收尚无完成证据。
5. **依赖来源说明需要核对。** 工具文档与源码将 pinyin4j 标为 Apache-2.0，但本地 2.5.1 POM 和[上游 POM](https://github.com/belerweb/pinyin4j/blob/master/pom.xml) 标 BSD，[上游 PinyinHelper.java](https://github.com/belerweb/pinyin4j/blob/master/src/main/java/net/sourceforge/pinyin4j/PinyinHelper.java) 文件头又标 GPL v2 或更高版本。已确认“Apache-2.0”声明无依据；实际采用版本的许可和署名尚须核实，不能把分支源码与发布包混为同一许可证。

## 后续继续时的顺序

先处理拼音工具自身的转换与边界缺陷、JDK 配置和资源一致性，补齐专用图标与真实验证记录；再跑 Android 门禁、隔离 V56 候选与后台联动验收；验证通过后按既有授权提交、推送、部署和线上只读验收。本次状态检查没有执行这些发布动作。

此前主线程、Kepler 和 Boyle 的正则工具分工及发布进度，已保存在 [REGEX_TESTER_ACCEPTANCE_2026-10-13.md](REGEX_TESTER_ACCEPTANCE_2026-10-13.md)。本次检查不将此前已完成工作重新列为未完成。

## 子智能体 Huygens：业务代码复核

子智能体只读检查已完成并结束。本表是源码审查结论，触发示例由源码推导，未另做动态复现；不能作为设备测试结果。

| 问题 | 触发与影响 | 源码位置 |
| --- | --- | --- |
| 声调与 ü 设置冲突（P1） | 数字声调选 `v` 或 `u:` 后切回符号声调，隐藏的 ü 设置仍保留；pinyin4j 不支持此组合，异常被吞掉，单字回退原汉字，多音字查询可能为空。 | `LocalPinyinRepository.kt:278`、`:208` |
| 轻声残留数字（P2） | 符号声调只转换末尾 1–4，未去掉轻声 5；词典“便宜”“为了”的结果仍含 `yi5`、`le5`。 | `LocalPinyinRepository.kt:307` |
| 扩展区汉字被误删（P2） | 统计按 Unicode 码点，完整拼音/首字母循环按 UTF-16 Char；`𠮷` 被统计为汉字，但关闭保留非汉字时被删除。应明确未收录汉字的行为。 | `LocalPinyinRepository.kt:51`、`:69` |
| 单字母音节分隔错误（P2） | 将长度为 1 的输出片段判为非汉字；无声调、连字符模式“阿姨”得到 `ayi`，无法输出 `a-yi`。 | `LocalPinyinRepository.kt:263` |
| 跨词多音字误判（P2） | “有空调”先匹配词典“有空”，把空读为 kòng，后面的“空调”无法命中；词组消歧仍有明显边界。 | `LocalPinyinRepository.kt:79`、`:544` |
| 旧任务/取消防护缺失（P2） | 普通 Flow `map` 同步执行转换，没有取消检查或结果版本验证；长文本处理中改输入或清空，旧计算仍继续并可能先回发旧输入/结果。UI 输入反馈也等待计算完成。 | `PinyinViewModel.kt:81`、`:108` |

补充核对：11 个 `@Test` 的数量属实，但所谓恢复测试没有重建 ViewModel 或恢复保存值；“便宜”测试只断言结果前缀，未检查 `yi5`。词典有 328 次赋值、326 个唯一词组，文档“约 300 条”基本相符，源码注释“约 2700 条”不实。

仍未验证：真实进程恢复、快速输入/清空时序、取消延迟、长结果排版与内存、大字号统计栏、TalkBack、真机主题表现。
