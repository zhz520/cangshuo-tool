# 正则表达式工具：进度与验收记录（2026-10-13）

## 目标

实现截图清单「开发应用 → 正则表达式」，作为本地离线工具上线：Android 客户端、服务端目录（V55）与后台管理同步，并完成生产部署与只读验收。

## 并行分工

本轮的编码、测试、部署由主线程与两个子智能体协作完成，全部改动直接落在共享工作区，主线程逐项复核后整合、构建与发布；子智能体未获得提交、推送或部署授权。

| 角色 | 负责内容 | 产出文件 |
| --- | --- | --- |
| 主线程（Codex） | 领域模型、仓库、ViewModel、页面、注册与图标、双语资源；集成构建、Lint、设备验收、生产发布与验收、文档 | `feature/regextester/`（5 个源文件）、`ic_regex_tester.xml`、`strings.xml`（中英）、`ToolboxAppContainer.kt`、`HomePresentation.kt`、`docs/REGEX_TESTER.md` 等 |
| 子智能体 Kepler | V55 目录 seed 与后台/统计验收脚本（中英文契约、后台读写守卫、停用/维护/软删除、种子重放、热门与 7/30/90 天统计、线上只读脚本） | `V55__seed_regex_tester_tool.sql`、`scripts/check_regex_tester_runtime.py`、`scripts/verify_regex_tester_deploy.py` |
| 子智能体 Boyle | 53 项 JVM 回归测试（Repository 35 + ViewModel 18），覆盖匹配/分组/替换语义、预算上限、取消与 SavedStateHandle 恢复 | `LocalRegexTesterRepositoryTest.kt`、`RegexTesterViewModelTest.kt` |

## 验证记录

- Android：全量单元测试 **475/475**；`assembleDebug` 与 `lintDebug` 通过（0 错误；本工具新增文件 0 条 Lint 发现）。
- 设备：Android 16 模拟器实测日期示例（2 处匹配、命名分组 year/month/day 展开）、字面替换 `Start date, end date`、1.5 倍字号与深色渲染；截图 `android/build/calc-emulator/regex-form.png`、`regex-groups.png`、`regex-replaced.png`、`regex-large-dark.png`。
- 隔离候选：**303/303**（复用共享隔离框架，创建与清理仅限临时库/容器）。
- 生产：HTTPS 只读验收 **58/58**（中英文契约、公开/管理目录一致、管理守卫、7/30/90 天统计、热门列表）；未写入生产用量。

## 交付与发布

- 提交：`6c6d132 feat: add RE2 regex tester tool`、`2fce4b7 docs: record the regex tester deployment`（随后修正 ROADMAP 批次编号并新增本记录）。
- 生产镜像：API `sha256:79518d05fd6e778f443fc040963e4d7c762bf1a8869cb70a2b9494edc60cd5b8`，回滚标签 `before-regex-v55`；五个容器健康（server / admin / media-browser / mysql / redis），admin 反代已 reload。
- 清理：隔离候选的暂存目录与镜像已删除；服务器仓库与 GitHub `origin/main` 同步。

## 遗留事项

- Wave 6 剩余平台解析与下载工具（全能下载器、资源嗅探、主页解析、作品提取、优惠券领取等，需域名白名单与合规声明）。
- 真机安装与 TalkBack 未在本轮执行，按发布验收节奏补做。
- 正则工具本身的已知边界（前后瞻/反向引用不支持、示例不承担业务校验）已记录在 [REGEX_TESTER.md](REGEX_TESTER.md)。
