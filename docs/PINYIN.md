# 汉字转拼音（pinyin）

代码 `pinyin`，分类 TEXT，模式 LOCAL，排序 95，中文名「汉字转拼音」。完全在设备本地执行；离线可用、无权限、无网络、无上传。页面位于 `feature/pinyin/`（Compose → ViewModel → UseCase → Repository），`PinyinToolDefinition` 注册到 ToolRegistry。服务端 V56 只登记目录元数据，转换全部在客户端完成，不新增执行接口。

## 源码查阅与适配

实际查阅日期：2026-10-13（初版）、2026-10-14（重写转换引擎并复核许可）。

| 实际阅读的源码 | 采用的思路与适配差异 |
| --- | --- |
| pinyin-data 的 [pinyin.txt](https://github.com/mozillazg/pinyin-data/blob/9193766130af24d2ac54230be979b2e98ac66223/pinyin.txt)、[数据合并规则](https://github.com/mozillazg/pinyin-data/blob/9193766130af24d2ac54230be979b2e98ac66223/merge_unihan.py)、[MIT 许可证](https://github.com/mozillazg/pinyin-data/blob/9193766130af24d2ac54230be979b2e98ac66223/LICENSE)，固定提交 `9193766130af24d2ac54230be979b2e98ac66223`（Unicode 17.0.0） | 采用合并后的字库，转为声调数字形式并随 APK 发送，保留其读音顺序；许可证原文保存在 `assets/licenses/pinyin-data.txt`。没有复制其 Python 生成代码，不引入运行期依赖。 |
| python-pinyin 的 [声调转换](https://github.com/mozillazg/python-pinyin/blob/master/pypinyin/style/_tone_convert.py)、[字符转换](https://github.com/mozillazg/python-pinyin/blob/master/pypinyin/style/_utils.py)（查阅 master，链接可变化） | 参考标调规则、`ü/v/u:` 与成音节鼻音的处理。本项目独立编写 Kotlin 格式化；参考库支持轻声数字 5，本工具固定省略 5 并在界面说明。 |
| TinyPinyin 的 [Pinyin.java](https://github.com/promeG/TinyPinyin/blob/master/lib/src/main/java/com/github/promeg/pinyinhelper/Pinyin.java)、[Engine.java](https://github.com/promeG/TinyPinyin/blob/master/lib/src/main/java/com/github/promeg/pinyinhelper/Engine.java)（master，链接可变化） | 采用词条优先和最长匹配思路；保留项目原有 326 条词表，不引入 Aho-Corasick。增加一位前瞻，修正「有空调」等相邻词条的读音冲突；不宣称具备完整自然语言分词。 |
| [belerweb/pinyin4j](https://github.com/belerweb/pinyin4j)（初版使用，现已移除） | 复核 2.5.1 的 POM 标注 BSD，而 `PinyinHelper.java` 源码头声明 GPLv2-or-later，许可声明不一致；且只支持单字、不覆盖扩展区码点。本轮移除该依赖，改用上面的 MIT 数据。初版本文档写成「Apache-2.0」是错误的，已纠正。 |

## 功能与特性

- **完整拼音**：按词组消歧后逐音节输出，支持声调符号（`pīnyīn`）、声调数字（`pin1yin1`，轻声不输出数字）与无声调三种样式。
- **ü 写法**：声调数字与无声调样式可选 `ü`、`v`、`u:`；声调符号样式固定使用 `ü`（`lǜ`、`nǚ`）。
- **大小写**：小写、大写、首字母大写。
- **间隔**：空格、连字符、无间隔；间隔只加在相邻音节之间，标点与英文不参与。
- **仅首字母**：取每个音节读音的第一个字母（如「中华人民共和国」→ `zhrmghg`），同样经过词组消歧（「重庆」→ `cq`）。
- **多音字查询**：逐字列出字库中的全部读音（去重，如「重: zhòng / chóng / tóng」）。
- **非汉字处理**：可选择保留或过滤英文、数字与标点；未收录读音的汉字始终原样保留并单独计数，绝不被静默删除。
- **统计**：按 Unicode 码点统计总字符、汉字、非汉字与未收录读音汉字的数量。
- **示例**：内置「重庆银行便宜音乐」「绿色女旅行」「阿姨有空调」「𠮷测试汉字」四个示例，一键填入。
- **复制与状态恢复**：结果可整体复制（长结果只显示前 12000 个字符，复制始终是完整结果）；输入与全部选项保存在 SavedStateHandle，页面重建后恢复并自动重新转换。

## 输入输出与边界处理

| 项目 | 限制 | 超限行为 |
| --- | --- | --- |
| 输入长度 | 100,000 个 UTF-16 单元 | 拒绝这次编辑，保留上一次输入，并提示上限；不崩溃、不截断 |
| 输出长度 | 1,000,000 个 UTF-16 单元 | 停止转换并提示「结果超过上限」，不返回半截结果 |
| 单次转换时间 | 3 秒（含首次字库加载） | 检查点停止并提示超时，可重试；串行执行避免旧任务与新任务同时占用资源 |
| 空输入 | 0 字符 | 显示引导文案，不执行转换 |
| 未收录汉字（如扩展 B 区 `𠮷`） | 字库覆盖 44,435 个码点 | 各模式均原样保留并计入「未收录读音」，多音字模式该行显示原字；已收录扩展 G/H/I 区及「〇」可转换 |
| 不完整 Unicode | 孤立高/低代理项 | 返回明确错误，不过滤或截断掩盖无效字符 |
| 词组消歧 | 326 条词组，最长 4 字 | 未收录词组回退为单字首选读音；跨词歧义（如「有空调」）按前瞻规则让位给后面的词条 |
| 取消 | 每次编辑/取消按钮都会终止上一次转换，并用版本号丢弃过期结果 | 旧任务不会覆盖新输入的结果 |

## 引擎实现

- 数据：`assets/pinyin/readings.tsv` 每行 `HEX<TAB>读音1,读音2…`；只在第一次完整加载后缓存，取消或失败不会保留半份字库。新建 UseCase 内的可取消互斥锁让同步转换逐个执行；字库和转换循环均每 256 步检查取消与时间预算。
- 词组消歧：从当前位置尝试 4→2 字最长匹配；命中后若「下一位开始存在更长词条且与当前词条末字读音冲突」则放弃当前匹配（例如「有空」在「有空调」中让位给「空调」，「重庆」不受影响）。
- 标调：按 python-pinyin 的规则定位元音；`ê` 与成音节鼻音 `m`/`n` 使用自带声调的形式（`ế`、`ń`、`ḿ` 等）。
- 取消与预算：转换过程通过 `PinyinCheckpoint` 每 256 次迭代检查协程状态与时间预算，配合 `viewModelScope` 任务取消，做到「新输入立刻生效、旧结果不再回写」。

## 界面与设计规范

- 遵循 `docs/ANDROID_DESIGN_SYSTEM.md`：`ToolGroupCard`、`ToolSectionHeader`、`ToolboxTextField`、`ToolboxSwitch`、`ToolboxTextButton`、`ToolResultRow`、`ToolboxLoadingIndicator` 等共享组件；选项使用 Material 3 `FilterChip`，`FlowRow` 自动换行；无硬编码颜色、圆角与文案。
- 结果区显示进行中（含取消按钮）、已取消（含重试）、失败、空结果与成功五类状态；长结果与未收录提示单独说明。
- 复制使用 `LocalClipboard` + `ClipEntry`，成功/失败都有 Snackbar 反馈。
- 中英文可翻译资源同步维护；四个汉字示例声明为 `translatable="false"`，英语使用共享回退；`pinyin_keywords` 两个语言各 7 项。

字库可以离线重建：`python scripts/build_pinyin_data.py <固定提交的pinyin.txt> --check` 核对现有资产，省略 `--check` 则重新生成。脚本核对源文件哈希并固定输出 LF 行尾：源 SHA-256 `621f8ca9eff8519f47e2b17b564fd318161e13bca07eea8c8e04993cd5d3b52e`，生成资产 **523,443 字节 / 44,435 行**，SHA-256 `9bc44a77c0b3534e7f9d0014d4449a8a37a7159e680d3a426787a569a1c9e3f9`。

## 验证与验收

- Android：2026-10-14 全量 **513/513**，其中拼音 **38 项**（原文件 23、新增回归 15），`assembleDebug` 与 `lintDebug` 通过，0 错误 / 86 警告，拼音新增文件无 Lint 发现。涵盖词组统计、Unicode、非法代理项、轻声、ü 选项、过滤分隔、字库取消、旧结果防护与实际修改后重建 SavedStateHandle。
- 服务端：Linux Maven 容器中 **226/226** 测试通过。
- 隔离候选：**309/309**，验证 V56、双语资源、许可与资产、后台 CRUD/权限、停用/维护/软删除、种子重放、热门榜、7/30/90 天统计，临时库/用户/容器已清理。
- 模拟器：Android 16 实测词组及 8 字统计、完整复制反馈、「有空调」、无声调与连字符、数字声调 v → 符号声调切换、首字母、多音字查询、罕见字保留、1.5 倍字体和窄屏深色；截图在 `android/build/calc-emulator/pinyin-*.png`。
- 生产发布及只读验收仍在本轮收尾中，完成后追加结果；不能据此认为设备已自动升级。用户真机安装、TalkBack 和低端硬件耗时尚未执行。

## 已知限制

- 词组表只覆盖 326 条高频多音字词，未收录的词语仍可能按单字首选读音输出；上下文消歧无法做到 100% 准确。
- 多音字查询一次列出字库中的全部读音，不区分词性、也不给出用例。
- 声调符号样式固定使用 `ü`，不提供 `v`/`u:` 变体（数字与无声调样式提供）。
- 首次打开工具需要加载字库（约 44,000 条），在老设备上首次转换会有可感知的短暂延迟；加载完成后转换即时完成。
