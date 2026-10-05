# UUID 生成器

唯一编码 `uuid`，分类 `DEV`，模式 `LOCAL`，排序值 120。`UuidToolDefinition` 注册到 ToolRegistry，首页、目录、搜索与推荐共用同一份定义；无需账号、网络或 Android 权限。服务端目录登记由 V6 迁移完成，本轮没有修改迁移、接口或依赖。

2026-10-04 本轮修复：修正 ViewModel 直接调用 `UUID.randomUUID()`、绕过已注入 `GenerateUuidUseCase` 的分层缺陷。生成与格式化现在都经过 `UuidViewModel → GenerateUuidUseCase → UuidRepository / LocalUuidRepository`；Repository 拆分为 `generateRaw(count)` 与 `format(raw, config)`，ViewModel 只保存原始 UUID 列表和 SavedState 选项；初始状态按已保存选项格式化，恢复时不再闪现默认格式。

## 实际开源参考

查阅日期：2026-10-04。以下链接为当日取得的分支文件，不是固定提交；没有把项目名称或 README 当作完整业务源码参考。

| 来源与实际阅读范围 | 采用思路 | Android 适配与差异 |
| --- | --- | --- |
| [OpenJDK `UUID.java`](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/UUID.java)（sha `f69ca8171cf66bcfbbb5942dea78a9381ebeab46`）：类文档、`Holder`、`version()` / `variant()` / `toString` / `fromString` 段落 | 随机 UUID 由 `Holder` 中的 `SecureRandom` 驱动；类文档引用 RFC 9562，version 4 表示随机生成，variant 2 为 IETF/Leach-Salz；`toString` 固定 8-4-4-4-12 | 继续使用 `java.util.UUID.randomUUID()`，不自行实现随机源或版本/variant 位操作；格式化只做展示层变换 |
| [f4b6a3/uuid-creator `UuidCreator.java`](https://github.com/f4b6a3/uuid-creator/blob/master/src/main/java/com/github/f4b6a3/uuid/UuidCreator.java)（sha `aec19727c54eec85a764137d52410f410d34345a`，实际取得前 80 行门面与导入）与 [`RandomBasedFactory.java`](https://github.com/f4b6a3/uuid-creator/blob/master/src/main/java/com/github/f4b6a3/uuid/factory/standard/RandomBasedFactory.java)（sha `32a569601a4d7e6b38dea6a10ab2551a54a96152`，完整工厂类） | v4 由独立随机工厂负责、随机源可替换；生成与字符串编解码职责分离 | 不引入第三方依赖；参考“生成与格式化分离”的接口形态，用 `generateRaw` 与 `format` 两个职责明确的方法表达 |
| RFC 9562 §5.4（UUIDv4） | 122 位随机、6 位版本、2 位 variant；标准 8-4-4-4-12 文本格式 | 文本由 `UUID.toString()` 产出；大写、去连字符、大括号属于展示选项，不改变底层 128 位值 |

说明：OpenJDK 文件实际取得类文档、`Holder`、`version()` / `variant()` / `toString` / `fromString` 等段落，未逐行读取整个文件；uuid-creator 实际取得门面文件前 80 行与完整 `RandomBasedFactory` 类。没有复制第三方业务代码，也没有新增第三方许可证资产。

## 实现分层

```text
UuidRoute / UuidScreen
  → UuidViewModel
  → GenerateUuidUseCase
  → UuidRepository / LocalUuidRepository
  → java.util.UUID.randomUUID()
```

- ViewModel 不再包含任何 `UUID` 调用或字符串拼接，只把 SavedState 选项与原始列表组合成 UI 状态。
- 生成与格式化都经过 UseCase；随机生成与展示规则位于 Repository，`Locale.ROOT` 保证格式化不受系统区域影响。

## 功能覆盖与规则

### 生成

- 数量快捷选项为 1、5、10、20、50、100；合法范围 1..100，ViewModel 与 Repository 都会 clamp。
- 切换数量或点击“重新生成”会产生全新的 v4 集合；切换大写、去连字符或大括号只重新格式化现有集合，不重新生成。
- 进程重建时数量与格式选项从 SavedState 恢复，UUID 列表重新生成；结果不跨进程保存。
- 随机源为 `java.util.UUID.randomUUID()` 内部的 `SecureRandom`；它适合唯一标识，不作为密码、密钥或安全令牌生成器使用。

### 格式

- 大写开关；连字符开关（关闭输出 32 位纯十六进制）；大括号开关。
- 变换顺序固定为：去连字符 → 大小写 → 大括号；单条复制与复制全部使用格式化后的文本。
- 初始状态按已保存选项直接格式化，不闪现默认小写连字符格式。

### 边界与不支持

- 只生成 UUID v4；不支持 v1/v3/v5/v7、命名空间 UUID、自定义随机源、输入解析/校验或文件导出。
- 不保证绝对唯一，只提供标准概率唯一性；不做重复检测（v4 碰撞概率极低，数量上限 100）。
- 不记录生成历史，无网络、无权限、无数据上传。

## 界面与交互

- 选项卡片与结果卡片分区，最大内容宽度 600dp，适配键盘 Insets。
- 结果列表使用等宽字体，单条可点击复制，支持复制全部；提供“重新生成”。
- 中英文资源完整，共 16 个 `uuid_*` key。

## 服务端目录登记

`V6__seed_uuid_tool.sql` 保持不变：code `uuid`、分类 `DEV`、模式 `LOCAL`、排序 120、`ENABLED`、推荐位；关键词覆盖 UUID、GUID、生成器、唯一标识、随机等。本轮没有新增迁移、API、依赖或权限。

## 验证与验收

2026-10-04 验证记录：

| 检查 | 结果 | 能证明的范围 |
| --- | --- | --- |
| 离线 `:app:assembleDebug :app:lintDebug` | BUILD SUCCESSFUL，1 分 16 秒 | 源码可编译并通过 Lint 错误门禁；0 错误、24 条警告，数量与修复前一致，UUID 源码无 Lint 问题 |
| 分层检查 | `UUID.randomUUID` 只出现在 `LocalUuidRepository.kt:12`；ViewModel 仅调用 UseCase 的 `generateRaw` 与 `format` | 文档所示分层与实际执行路径一致 |
| 中英资源 key | `uuid_*` 各 16 个，集合一致、无重复、全部被引用 | 英语切换不会因缺 key 回退中文 |
| JBR 验证（Android Studio 自带 JDK，临时程序已删除） | 10,000 个 UUID：`unique=10000`、`versionErrors=0`、`variantErrors=0`、`formatErrors=0`、`duplicates=0`；去连字符/大写/大括号变换长度正确 | 验证了 v4 版本/variant、标准格式、唯一性与三种展示变换 |
| `git diff --check` | 通过 | 已跟踪差异没有空白错误 |

未执行：单元/仪器测试、Android 运行时样例、Preview 渲染、真机或模拟器、英语系统切换、剪贴板与进程重建验收。

建议人工样例（待设备执行）：

1. 数量切换 1、5、10、50、100：列表数量与选项一致，无卡顿。
2. 切换大写、去连字符、大括号：现有集合内容不变，只改变显示；组合顺序与文档一致。
3. “重新生成”：全部条目变化，数量保持不变。
4. 单条复制与复制全部：内容与界面一致，复制全部按行分隔。
5. 旋转屏幕或短暂切后台再回来：数量与选项保留；进程被系统回收重建后列表重新生成，选项仍保留。

剩余风险：唯一性依赖标准随机源的概率保证；本轮未建立自动化回归测试，设备与剪贴板行为仍待验收。
