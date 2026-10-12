# 亲戚称呼换算

编码 `kinship`，`CALC / LOCAL`，排序 75。Compose → KinshipViewModel → AskKinshipUseCase → KinshipRepository / LocalKinshipRepository；查询使用随包数据表全部本地完成，无网络、账号或权限要求。ToolRegistry 与 V48 工具目录使用相同名称、描述、图标与关键词。

## 开源参考与适配

实际源码查阅日期：2026-10-12。参考仓库 [mumuy/relationship](https://github.com/mumuy/relationship)（MIT，本次提交 `35c44427befb6fd59e16f583b5b92a680a7948db`，3734 stars）：

| 来源 | 实际采用内容 | 适配差异 |
| --- | --- | --- |
| [src/module/selector.js](https://github.com/mumuy/relationship/blob/35c44427befb6fd59e16f583b5b92a680a7948db/src/module/selector.js)（blob `1ec7ffd1299d1d94e1b7d43f423cbaa263d58257`）、[src/module/id.js](https://github.com/mumuy/relationship/blob/35c44427befb6fd59e16f583b5b92a680a7948db/src/module/id.js)、[src/module/map.js](https://github.com/mumuy/relationship/blob/35c44427befb6fd59e16f583b5b92a680a7948db/src/module/map.js)、[rule/filter.js](https://github.com/mumuy/relationship/blob/35c44427befb6fd59e16f583b5b92a680a7948db/src/module/rule/filter.js)、[rule/replace.js](https://github.com/mumuy/relationship/blob/35c44427befb6fd59e16f583b5b92a680a7948db/src/module/rule/replace.js)、[rule/expression.js](https://github.com/mumuy/relationship/blob/35c44427befb6fd59e16f583b5b92a680a7948db/src/module/rule/expression.js) | 逐行阅读选择器解析（词义扩展、同义词、排序前缀）、关系链过滤/展开（44 条规则）、id 正反算、称谓/关系链/合称三种输出与自然语言句式（10 条表达式规则），将其完整移植为 Kotlin（`KinshipEngine`） | 未复制代码。为避免移动端内存与启动开销，数据表在 6 段关系链处裁剪（见下）；`switchMode` 的多模式（北方/广东话）未接入；正则语义与字符串切片按 Kotlin/JVM 语义等价重写，并以差分测试验证行为一致 |

## 数据与生成

- 上游数据文件（main/branch/prefix/multiple/pair/input/sort，合计约 70KB）在构建期由 Node 脚本 `temp/rel-probe/dump.mjs` 加载并按 `map.js` 规则合并、展开、裁剪后导出为 `android/app/src/main/assets/kinship/{mode,input,sort,pair}.json`（mode 1.2MB，其余 <2KB）。脚本同时生成差分语料 `android/app/src/test/resources/kinship/golden.tsv`。
- 裁剪口径：仅保留 6 段以内关系链（4836 条唯一链、55704 条称谓别名，覆盖曾祖/高祖/天祖与玄孙/来孙等常见深度）；在导出前先调用参考实现的 `switchMode('default')`，避开其 import 期把 input/sort 别名混入查询表的状态差异，与语料生成时的稳定行为一致。
- 重放脚本、裁剪补丁与语料均保存在 `temp/rel-probe/`（本地工具目录，不进 Git 交付物）；重新生成命令：`node dump.mjs ./relationship-pruned`。

## 功能与边界

- 输入「爸爸的妈妈」这类关系链，输出标准称谓（奶奶）；支持词义扩展与同义词（舅家的哥哥 → 舅表哥；幺/细/满/晚/末/尾/幼 前缀按「小」处理；大/小/一~十 排序前缀生成 大舅/二舅 等）。
- 三种输出：称谓（多个候选按参考顺序返回）、关系链（如 爸爸的妈妈）、关系合称（如 爸爸+妈妈 → 夫妻）。
- 选项：我的性别（不限/男/女）、称呼方向（我称呼对方 / 对方称呼我）、自动取最短关系（optimal，处理堂/表/长幼等对称场景）、相对对象（可选，用于「X 是 Y 的谁」类查询）。
- 自然语言句式：直接输入「爸爸的哥哥叫什么」「爸爸的哥哥是谁」「爸爸和妈妈是什么关系」等 10 类句式会被自动识别并转换。
- 边界：输入上限 60 字符；空输入显示引导；超出 6 段或数据中不存在的说法返回「未找到」提示；输入过大拒绝并保留原值。
- 明确不支持：方言/地区模式（粤语等）、族谱多代以上（>6 段）、同名多义的人工消歧、把结果直接写入通讯录等。

## 状态与界面

SavedStateHandle 保留输入、选项与相对对象；快速输入 chips（爸爸/妈妈/哥哥/姐姐/弟弟/妹妹/老公/老婆/儿子/女儿）按「名 + 的」拼接，另有「删除末段」「清空」；查询在 `Dispatchers.Default` 上执行，避免大数据表解析阻塞主线程。结果卡支持多候选（长按可选择）与一键复制。复用共享 `ToolGroupCard`、`ToolboxTextField`、`FilterChip`、`ToolboxSwitch` 与主题；中英资源齐全。

## 实际验证

2026-10-12：`KinshipEngineTest` 4 项，其中差分语料 600 例（随机关系链 1–3 段、性别/方向/类型/最短关系变体、自然语言句式与 10 条经典用例，期望值由参考实现生成）全部一致；另有经典用例（奶奶/伯父/岳父/曾祖母/舅表哥/姑表哥/外甥/弟妹/自己）、自然语言与三种输出模式、超长链与未知说法返回空。`KinshipViewModelTest` 3 项覆盖 chips 拼接/删除/清空、反向与性别切换、超长输入保留原值。本批 Android 全量 **332/332** 测试通过（62 个套件），`assembleDebug` 与 `lintDebug` 通过（0 错误、83 警告；新增文件无 lint 告警）。

2026-10-12 模拟器（Android 16）实测：点按快速输入 chips「爸爸」「妈妈」得到 爸爸的妈妈，结果卡显示「奶奶」；清空后仅点「爸爸」并切换「对方称呼我 + 男」，结果显示「儿子」。中文输入法（adb 无法注入中文）、大字号与 TalkBack 仍待人工验收；未接入方言模式；6 段上限之外的结果以「未找到」呈现，未做自动降级提示。
