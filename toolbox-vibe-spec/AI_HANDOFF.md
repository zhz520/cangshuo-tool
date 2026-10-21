# AI 接手说明（新窗口 / 换模型）

本文件给新的 AI 会话使用：新开窗口或切换模型时，把下面「开场消息」整段发给 AI，让它从仓库读取现状，不依赖人复述。

## 长期协作要求（用户 2026-10-17 明确确认）

用户要求持续自主推进，不要因单个工具完成或普通验证步骤停下来等待“继续”。每轮先读取本文件和 ROADMAP，选择最小可完整交付的未完成项；完成代码、测试/Lint、文档、提交/推送，并按既有授权在验证通过后部署生产。只有遇到自动审批拒绝、缺少必要凭据/设备或真正不可逆风险时才暂停并明确说明。镜像轴映射回归已提交为 `63f6280`。

## 最新增量（2026-10-21，Apifox 中文化）

用户补充要求「中文」。已在源注解翻译54个英文接口名称、26处分组声明（25个不同分组），并把默认成功说明改为中文；74操作/60路径/127模型保持不变。已有OpenApiExportTest **2/2** 通过，无新增测试。去除显示名称/分组/说明后，与旧快照的路由、参数、请求体、响应模型、鉴权及operationId完全一致。根AGENTS与APIFOX文档明确后续接口显示文本使用中文；协议字段不翻译。推送后由已有Webhook同步，须核对Apifox实际中文页面。本任务无业务、数据库或生产部署变化。

## 最新增量（2026-10-21，Apifox 接口同步）

用户指定接口文档同步到 https://app.apifox.com/project/8952528。已在登录后的「Git & Branches」确认绑定 `zhz520/cangshuo-tool/main` 且 Webhook 已激活。`docs/openapi.json` 由真实 Springdoc 导出，覆盖74操作/60路径/127模型；补齐元信息/稳定operationId/Bearer声明/常见错误/traceId，以及Zepp成功DTO、能力同名模型和媒体流格式。`OpenApiExportTest` 逐接口核对MVC路由和实际AuthorizationManager，且断言DB/Redis/会话无业务调用。服务端227/227、package和快照一致性检查通过。`scripts/export_openapi.py` 可重复生成；CI对照快照并保存artifact。此任务无数据库/Android/生产部署变化。推送后须在Apifox核对新提交和接口列表，详细验收记录见 `../docs/APIFOX.md`；后续契约变更必须更新快照再推送。

已完成推送与页面验收：`99063de` 初次同步、`c57f083` 修复Apifox兼容性和完整性提示。最终服务端 **228/228**、package与快照检查通过，**573** 处正则通过JavaScript RegExp编译；会员计划大小写校验语义保持不变并有新回归。Apifox显示`Paths (60)`、前置URL和业务说明，问题面板 **0错误/3可选元信息提示**（项目许可证/URL/公开邮箱未擅自设置）。`99063de` 的CI39545371040成功；最终`c57f083` CI39546039313与Release39546039346均成功，随后才提交本轮验收记录。本轮`temp/openapi/`临时目录已清理，截图保存在`server/target/apifox-sync-2026-10-21.png`。详情见APIFOX.md；接口文档同步已完成，下一步按用户最新要求或ROADMAP继续。

## 最新增量（2026-10-20，电子木鱼已上线）

`wooden_fish`（OTHER/LOCAL、排序642、V90）已实现并上线：本机合成WAV+SoundPool、有界触感、累计/今日计数、21/108/1080目标、七档里程碑、前台自动节奏上限5分钟、声音/触感/动画开关、确认重置与摘要分享。参考 MIT sodared/electronic-wooden-fish 固定 commit 的计数/跨日/成就组织，未复制代码/文案/音频。Android双变体879/879（128 suites）、双Lint0错误/91既有警告且新文件无finding；服务端226/226、静态22/22、发行物门禁通过（未签名）。Android16实测4844字节本机WAV、五次点击持久化、首里程碑、自动递增和后台停止、摘要分享、重置，及深色/2倍字体/720×1600。真机音色/触感/TalkBack待验收。详见 `../docs/WOODEN_FISH.md`。

提交 `99b6c43` 已推送并部署；严格隔离193/193、生产HTTPS93/93、Flyway90/90失败0、五容器healthy、官网/后台/健康200。生产API `sha256:cd162ca7b9a167864a1d798a3d963cf71cfd929ede5a317afaf913e5836fad25`；回滚标签 `before-wooden-fish-v90` 保留代码 `43612b6` 与API `sha256:f298c091…`。后台站点仍为 V89 镜像。

代码提交 `99b6c43` 的CI **39410111930** 与Release artifacts **39410111917** 均成功。

## 最新增量（2026-10-20，振动器已上线）

`vibrator_patterns`（OTHER/LOCAL、排序641、V89）已实现并上线：七种有界波形、50–2000ms脉冲、5–100%映射1–255振幅、无马达/仅开关能力降级、完成记录、手动/后台停止、20条历史、CSV分享；新增普通 `VIBRATE` 权限并同步应用内中英说明、权限页及隐私页。参考 MIT LexiLabs-App/basic-haptic 固定 commit，未复制代码。Android双变体868/868（127 suites）、双Lint0错误/91既有警告且振动器无finding；服务端226/226、静态22/22、发行物门禁通过（未签名）。Android16实测虚拟马达真实振幅0.349（70%波浪中间35%脉冲）与89/255一致，完成880ms历史落盘，手动停止/后后台不入库，深色/2倍字体/720×1600通过。真机手感/TalkBack待验收。详见 `../docs/VIBRATOR_PATTERNS.md`。

提交 `43612b6` 已推送并部署；管理员镜像的权限/隐私页通过内容检查。严格隔离193/193、生产API HTTPS93/93、Flyway89/89失败0、五容器healthy、官网/后台/健康200。生产API `sha256:f298c091340118906c7c117c80a108e7df27385f90e62810eb8d359798675088`、管理员站点 `sha256:fe1587514202f23bbabbc7c178d4362337936f127a2ce853e803e49cb51d3773`；回滚标签 `before-vibrator-v89` 保留代码 `8fc121e`、API `sha256:b6b63afd…` 和站点 `sha256:3cdd847d…`。下一步按ROADMAP继续未完成工具；服务端和静态站点发布不代表用户手机已更新。

代码提交 `43612b6` 的 CI **39405621111** 与 Release artifacts **39405621188** 均成功。

## 最新增量（2026-10-20，答案之书已上线）

`answer_book`（OTHER/LOCAL、排序640、V88）已完成并上线：60条原创双语短句、三主题与全部池、跨进程不重复轮次、收藏、50条历史、全文回看/滚入/复制/分享、清空保持与损坏重置。参考 MIT vikiboss/60s 固定源码，仅借鉴条目/随机选择流程，未复制代码或答案。Android双变体855/855（126 suites）、双Lint0错误/91既有警告且新文件无finding；服务端226/226、静态18/18；APK/AAB门禁通过（未签名）。Android16已验证主题/抽取/收藏/历史/分享、实际JSON、清空/进程恢复、选中滚入、真实损坏JSON与确认重置、深色/2倍字体/窄屏；中文实际页面、真机/TalkBack待验收。详见 `../docs/ANSWER_BOOK.md`。

代码提交 `8fc121e` 已推送并部署；严格隔离189/189、生产HTTPS89/89、Flyway88/88失败0、五容器healthy、官网/后台/健康200。生产镜像 `sha256:b6b63afdbc8804e3cc68f9fccea262d8c7326108db73f08e0ad3c20506c429c5`；回滚标签 `before-answer-book-v88` 保留代码 `6e3dee3` 与镜像 `sha256:07dc8e255…`。本轮另完成手速测试V87；其最终文档提交 `4879b22` 的CI39392595868与Release39392595972均成功。按ROADMAP继续未完成工具，不重做已上线的手速和答案之书。

答案之书代码提交 `8fc121e` 的CI **39395450792** 与Release **39395450686** 均成功。

## 最新增量（2026-10-20，手速测试已上线）

`tap_speed`（OTHER/LOCAL、排序 639、V87）已实现并上线：1/5/10/30/60 秒、首击开始、单指/多指规则、按下事件计时、平均/逐秒峰值、曲线、输入来源标记、20 组完整历史、CSV/分享和中断恢复。参考 MIT sopheadave/click-speed-test 固定源码 commit，未复制源码或素材。Android 双变体各 843/843（125 suites），双 Lint 0 错误/91 既有警告，服务端 226/226、静态 14/14，发行物门禁通过（未签名）。Android 16 用真实双指事件验证三组手势单指记3次/多指记6次；长按键盘只计1次并单独标记，历史落盘/CSV分享/中断/深色/2倍字体/窄屏均验证。详情见 `../docs/TAP_SPEED.md`。

提交 `6e3dee3` 已推送并上线；严格隔离 185/185，生产 HTTPS 85/85，Flyway 87/87、失败0，五容器 healthy，官网/后台/健康200。生产 API `sha256:07dc8e2559475feb7d73ef0352863f5fedc38649cad1e1a5d7671c8bd527f7ee`，Git/Docker 回滚标签 `before-tap-speed-v87` 保留 `6f09cf2` 与 API `sha256:3b27a192…`。真机和 TalkBack 人工验收未完成。继续沿清单单项推进下一工具，不重复开发手速测试。

## 最新增量（2026-10-20，反应力测试已上线）

`reaction_test`（OTHER/LOCAL、排序 638、V86）已完成并上线；随机提示、首帧 draw 到触摸按下计时、抢跑/超时、多轮统计、本机最近 20 组历史、CSV 与分享。参考 GPL-2.0 Dakostu/TapIt 固定 commit，仅流程概念，未复制代码。Android Debug/Release 各 827/827（124 suites），双 Lint 0 错误/91 既有警告且新工具无 finding；服务端 226/226、静态 14/14、release artifact gate 通过。Android 16 模拟器验证三轮真实提示与触摸、历史落盘、CSV/分享/确认、抢跑/超时/后台中断、深色和 720×1600 / 2 倍字体；真机、硬件时序校准与 TalkBack 仍未验收。

提交 `6f09cf2` 已推送并部署；严格隔离 **185/185**，生产 HTTPS **85/85**，Flyway **86/86** 且失败 0，五个容器 healthy、官网/后台/健康均 200。生产镜像 `sha256:3b27a192cd756c3f393fd92268f44a1c16f9c70bf942c96822735214e1964a6c`；Git/Docker 回滚标签 `before-reaction-v86`，原生产代码 `b50cec9`、镜像 `sha256:f867f8be…` 保留。生产运行验收脚本使用宝塔现有 `/www/server/panel/pyenv/bin/python3`，不要假定 PATH 有 `python3`（系统 `/usr/bin/python` 为 2.7）。详见 `../docs/REACTION_TEST.md`。上一轮两个明确临时验收/部署目录已安全清理；SSH 私有文件保留。下一步沿 ROADMAP 继续未完成的 Wave 2 工具，不重做计时器或反应力测试。

代码提交 `6f09cf2` 的 GitHub CI **39388275893** 与 Release artifacts **39388275902** 均成功。Debug APK 为 `android/app/build/outputs/apk/debug/app-debug.apk`，Release APK/AAB 门禁通过但未签名；不要把服务端发布写成用户手机已更新。

## 最新增量（2026-10-18 第二十三批）

计时器 `countdown_timer`（LIFE/LOCAL、排序 637、V85）已完成并上线：1 秒–23:59:59 倒计时、九档快捷时长、自定义时分秒、名称、暂停/继续/确认重置/加 1 分钟、保持屏幕、`specialUse` 前台服务、通知栏暂停/继续/加时、完成提醒、单调时钟截止点、进程重建和重启后的墙钟追赶；计时状态只保存在 Android 私有 SharedPreferences。参考 MIT QuickTimer 与 MIT Micro Timer 固定 commit 的状态机、截止点、持久化和服务概念，未复制源码或资源。只读复审发现并推动修复了 `ACTION_STOP` 自递归前台启动风暴、页面/服务完成提醒竞态、截止瞬间继续/暂停/加时、最大时长溢出、墙钟回拨、idle 草稿被覆盖、权限 Activity ContextWrapper、大字体表单宽度和通知重置绕过确认。

Android Debug/Release 各 **814/814**（123 suites），两种构建 Lint **0 错误/91 条既有警告** 且计时器文件无 finding；Debug APK **26,686,106** 字节、Release APK **17,503,073** 字节、AAB **16,899,422** 字节，Release artifact 门禁通过。服务端 JDK 21 **226/226**、静态契约 **109/109**，后台 `vue-tsc --noEmit` 与 Vite 生产构建通过。Android 16 模拟器验证通知权限拒绝/授权、通知栏暂停/继续/加时、进程被杀服务重启、重置清理、受控 5 秒完成提醒、唤醒锁获取/释放、深色、2 倍字体和 720×1600 布局；真机 TalkBack 与厂商省电策略待后续设备验收。提交 `ae7a047`、`b50cec9` 已推送；严格隔离候选 **280/280**，生产 HTTPS **180/180**；生产 API 镜像 `sha256:f867f8beaf53f31bfda829501e0cb0564137f17e0326afee1c6d5596a4b21a6b`，Git/Docker 回滚标签 `before-countdown-v85`，Flyway **85/85** 且失败 0，官网/后台/健康均 200、五容器 healthy，CI `39119507915` 与 Release `39119507829` 成功。详见 `../docs/COUNTDOWN_TIMER.md`。下一步沿 ROADMAP 继续下一个未完成工具，不等待重复“继续”。

## 上一轮增量（2026-10-18 第二十二批）

经期记录 `period_tracker`（LIFE/LOCAL、排序 636、V84）已完成并上线：Room v14 本地经期起止/进行中、流量、8 类症状、6 类情绪和备注，六周固定日历、实际/预计/参考窗口标记、历史编辑与确认删除、平均周期/经期、当前周期天数和下一次估算；设置保存在私有 SharedPreferences，未接入网络和云同步。参考 MIT Mensinator 与 MIT CycleCare 固定 commit 的日历、区间预测和隐私边界，未复制源码或资源。审查修复了 48dp 日历触控、非颜色状态图例、TalkBack 起止/选中语义、全宽弹窗、结束日期被静默清空、备注静默截断、周期日文案和隐私政策覆盖。

Android Debug/Release 各 **803/803**（121 suites），两种构建 Lint **0 错误/91 条既有警告** 且经期记录文件无 finding；Debug APK **25,879,200** 字节、Release APK **17,445,441** 字节、AAB **16,847,638** 字节，Release artifact 门禁通过。服务端 JDK 21 **226/226**、静态契约 **129/129**，后台 `vue-tsc --noEmit` 与生产构建通过。Android 16 模拟器从真实 Room v13 升 v14 并保留历史，验证新增/编辑/确认删除、预测与参考窗口、深色、2 倍字体和 720×1600 表单；真机 TalkBack 细读仍待验收。提交 `2ef395f` 已推送；严格隔离候选 **300/300**，生产 HTTPS **200/200**；生产 API 镜像 `sha256:5a720b15a5b8970ef1cb707756ebd4338c9285269d9156a9eafe656bb63c61f9`，Git/Docker 回滚标签 `before-period-v84`，Flyway **84/84** 且失败 0，官网/后台/健康均 200、五容器 healthy。详见 `../docs/PERIOD_TRACKER.md`。下一步沿 ROADMAP 继续下一个未完成工具，不等待重复“继续”。

## 上一轮增量（2026-10-18 第二十一批）

白噪音 `white_noise`（LIFE/LOCAL、排序 635、V83）已完成并上线：六种实时程序化声层（白/粉/褐噪音、雨声、海浪、风扇）、独立层与总音量、专注/雨夜/睡眠场景、15–180 分钟单调计时和 15 秒平滑淡出、设置持久化、媒体前台服务与通知暂停/继续/停止、AudioFocus 和耳机断开暂停、播放/暂停状态的 deadline 保留恢复，以及深色、2 倍字体、窄屏和 TalkBack。参考 MIT chmouel/noise-timer 与 MIT Chromatone 固定 commit 的 DSP/服务概念，未复制源码或素材。审查修复了暂停焦点释放、最后一层状态分叉、定时淡出不复位、失败假播放、`stopSelfResult`、音频线程复用/退出竞态、失败引擎拒绝复用和通知授权后刷新。

Android Debug/Release 各 **796/796**（119 suites），两种构建 Lint **0 错误/91 条既有警告** 且白噪音文件无 finding；Debug APK **25,631,975** 字节、Release APK **17,332,477** 字节、AAB **16,747,960** 字节，Release artifact 门禁通过。服务端 JDK 21 **226/226**、静态契约 **143/143**，后台 `vue-tsc --noEmit` 与生产构建通过。Android 16 模拟器验证权限拒绝/允许、真实媒体通知、通知栏控制、场景、最后一层关闭、播放与暂停进程恢复、深色、2 倍字体和 720×1600 布局。提交 `01ee2ea`、`3a27b2d` 已推送；严格隔离候选 **314/314**，生产 HTTPS **214/214**；生产 API 镜像 `sha256:f60078958ba137e5f460b1cc77a032b5b6ac8d56afd3049114607d93f3812bfc`，Git/Docker 回滚标签 `before-white-noise-v83`，Flyway **83/83** 且失败 0，官网/后台/健康均 200、五容器 healthy。详见 `../docs/WHITE_NOISE.md`。下一步沿 ROADMAP 继续下一个未完成工具，不等待重复“继续”。

## 上一轮增量（2026-10-18 第二十批）

订阅管理 `subscription_manager`（LIFE/LOCAL、排序 634、V82）已完成本地交付：Room v13 本地订阅资料、周/月/季/半年/年与自定义周期、整数分月/年汇总、持久化账单锚点和月末不漂移续费、自动续费有效日期、记录续费、暂停/恢复、搜索筛选排序、500 条事务上限、双语与 SavedState。通知权限按需申请，存在提醒订阅时每日 09:00 使用无需精确闹钟权限的 `AlarmManager` 检查，并在重启、时间/时区/日期变化后重排；摘要通知使用自动续费后的有效日期。参考 Apache-2.0 Wallosmobile 与 MIT SubGuard 固定 commit 的续费/统计/提醒概念，未复制源码或资源。

Android Debug/Release 各 **789/789**（117 suites），两种构建 Lint **0 错误/91 条既有警告** 且订阅管理文件无 finding；Debug APK **25,482,728** 字节、Release APK **17,275,433** 字节、AAB **16,702,293** 字节，Release artifact 门禁通过。服务端在 JDK 21 下 **226/226**、静态契约 **152/152**，后台 `vue-tsc --noEmit` 与生产构建通过。Android 16 模拟器验证新增、金额汇总、到期状态、权限申请、真实通知、续费推进、深色、2 倍字体，并从 v12 升 v13 保留 `calculator` 最近使用记录。独立审查推进修复账单锚点、自动续费提醒、权限卡、重排广播、删除确认和数据库流重试。提交 `dbb9d46` 已推送；严格隔离候选 **323/323**，生产 HTTPS **223/223**；生产镜像 `sha256:b1991f330085d95c951ac7ee89f9eb9b0536d7568aae37dc597969dfb4a421b0`，Git/Docker 回滚标签 `before-subscription-v82`，Flyway **82/82** 且失败 0，官网/后台/健康均 200、五容器 healthy。详见 `../docs/SUBSCRIPTION_MANAGER.md`。下一步沿 ROADMAP 继续下一个未完成工具，不等待重复“继续”。

## 上一轮增量（2026-10-18 第十九批）

物品资产 `inventory_assets`（LIFE/LOCAL、排序 633、V81）已完成本地交付：Room 本地物品/分类/位置/数量单位/分单位单价/低库存阈值/购买和到期日期，已知价值、低库存、30 天内到期和过期统计，名称/备注/位置搜索、四种筛选、五种排序、详情编辑删除、确认删除、同条目并发保护的原子 `+1/-1`、1000 条上限、双语、深色和 2 倍字体布局。参考 Apache-2.0 Google Inventory App 固定 commit 的实体、DAO、列表/详情和 CRUD 概念，未复制源码或资源。

Android Debug/Release 各 **778/778**（115 suites），两种构建 Lint **0 错误/91 条既有警告** 且物品资产文件无 finding；Debug APK **24,748,008** 字节、Release APK **17,160,921** 字节、AAB **16,597,035** 字节，Release artifact 门禁通过。服务端在 JDK 21 下 **226/226**、静态契约 **132/132**，后台 `vue-tsc --noEmit` 与生产构建通过。Android 16 模拟器验证新增/编辑、金额与低库存重算、日期、筛选、快捷增减、详情、删除确认、深色、2 倍字体和键盘布局，并从真实 Room v11 升 v12 保留 `calculator` 最近使用记录。独立审查修复了 Room 插入上限误报、宽屏对话框宽度、TalkBack 条目上下文和连续同类型消息去重问题。提交 `5104cc3`、`75353e1`、`e5c61ae` 已推送；严格隔离候选 **303/303**，生产 HTTPS **203/203**；生产镜像 `sha256:5be4301f4029ce3a0f272b1e4b696b7e4a71f6024e083a8ab30ec4c98360d47f`，Git/Docker 回滚标签 `before-inventory-assets-v81`，Flyway **81/81** 且失败 0，官网/后台/健康均 200、五容器 healthy。详见 `../docs/INVENTORY_ASSETS.md`。下一步沿 ROADMAP 继续下一个未完成工具，不等待重复“继续”。

## 更早增量（2026-10-18 第十八批）

番茄专注 `pomodoro_focus`（LIFE/LOCAL、排序 632、V80）已完成并上线：绝对截止时间驱动的专注/短休息/长休息状态机、自定义时长与轮数、自动切换、暂停/继续/跳过/确认重置、专注名称、前台服务常驻通知及暂停/跳过操作、完成提醒、Room v11 历史、今日/近七日/当前与最长连续/累计统计、双语资源和深浅色响应式布局。2 倍字体和真实 1 分钟计时完成通知、后台更新、通知栏暂停和统计历史验收；并从真实 Room v10 升 v11，保留 2 条最近使用记录。参考 MIT Arijit-05/Fomodoro 固定 commit 的服务、阶段、预设和统计概念，未复制源码或资源。

Android Debug/Release 各 **768/768**（113 suites），两种构建 Lint **0 错误/91 条既有警告** 且番茄专注文件无 finding；Debug APK **25,138,121** 字节、Release APK **17,084,005** 字节、AAB **16,516,127** 字节，Release artifact 门禁通过。服务端 **226/226**、静态契约 **118/118**，后台 `vue-tsc --noEmit` 与生产构建通过。提交 `d1043e2` 已推送；严格隔离候选 **289/289**，生产 HTTPS **189/189**；生产镜像 `sha256:c9fa69f09a43ad801f7d9f70ede6c88bbaeb034b66e486c1b762a5148551d06c`，Git/Docker 回滚标签 `before-pomodoro-v80`，Flyway **80/80** 且失败 0，官网/后台/健康均 200、五容器 healthy。详见 `../docs/POMODORO_FOCUS.md`。下一步沿 ROADMAP 继续下一个未完成工具，不等待重复“继续”。

## 更早增量（2026-10-18 第十七批）

纪念日 `anniversary_countdown`（LIFE/LOCAL、排序 631、V79）已完成并上线：Room 本地重要日期、单次/每月/每年重复、当天语义、未来三次、闰日与短月钳制、完整年数、五类分类、置顶、搜索、四种筛选、四种排序、农历/生肖详情、双语 UI、500 条原子上限和 SavedState。2 倍字体加软键盘验收发现编辑正文会被压缩，已改为按可用高度布局；紧凑高度时正文与操作一起滚动，普通高度保留固定操作区。参考 MIT mark216tw/anniversary-countdown-android 固定 commit 的日期语义和 MIT lunar-java 的农历显示，未复制源码或资源。

Android Debug/Release 各 **754/754**（111 suites），两种构建 Lint 均 **0 错误/91 条既有警告** 且纪念日文件无 finding；Debug APK **24,726,812** 字节、Release APK **16,990,732** 字节、AAB **16,436,932** 字节，Release artifact 门禁通过。服务端 **226/226**、静态契约 **111/111**、同步 SQLite **30/30**，后台 `vue-tsc --noEmit` 与 Vite 生产构建通过。Android 16 模拟器验证年度/月度/单次事件、搜索筛选排序、置顶编辑删除、农历与未来日期、深色/2 倍字体/键盘布局，并从真实 Room v9 升 v10 保留 12 条最近使用。

提交 `eb38d7b` 与验收脚本修复 `e7c5e8f` 已推送。Release CI 另修复 `4910068`、`f7af986`、`f1ba3b6`；最新 CI `39054967858` 与 Release `39054967845` 均成功。严格隔离候选 **283/283**，生产只读 HTTPS **182/182**；生产镜像 `sha256:a127bf430d0915abb15e555fc7284dfb76ab87813d228f110405de01ee85f34f`，Git/Docker 回滚标签 `before-anniversary-v79`，Flyway **79/79** 且失败 0，官网/后台/健康均 200、五容器 healthy。详见 `../docs/ANNIVERSARY_COUNTDOWN.md`。下一步沿 ROADMAP 继续下一个未完成工具，不等待重复“继续”。

## 最新增量（2026-10-18 第十六批）

习惯打卡 `habit_tracker`（LIFE/LOCAL、排序 630、V78）已完成并上线：Room 本地习惯定义与逐日打卡、每周星期安排、今日/历史打卡、当前与最佳连续、本周进度、近 30 天完成率、28 天历史、搜索/今日/归档筛选、四种排序、归档日冻结统计、100 习惯/10000 记录原子上限、归档只读和确认删除/清历史。独立只读审查发现并推动修复周中创建统计、跨午夜刷新、并发写入、失败草稿、归档编辑/统计、2 倍字体圆点和 48dp 触控格。参考 GPL-3.0 iSoron/uhabits 固定 commit 的习惯/条目/连续/卡片概念，未复制源码、资源或依赖。Android 741/741、APK 24,166,555 字节、Lint 0 错误/91 既有警告（习惯无 finding）；服务端 226/226、静态契约 102/102；模拟器验证创建/编辑、星期安排、打卡/撤销、统计、历史、归档/恢复、搜索筛选排序、归档只读、深色/2 倍字体/键盘，并从真实 Room v8 升 v9 保留 12 条最近使用。提交 `fa2bdf5` 已推送；隔离候选 274/274、HTTPS 173/173，生产镜像 `sha256:bb1da6efd391c159ff9c3030a1e18afefb02c3926795283078a62f968c287b98`，Git/Docker 回滚标签 `before-habit-tracker-v78`，Flyway 78/78、官网/后台/健康均 200、五容器 healthy。详见 `../docs/HABIT_TRACKER.md`。

## 上一轮增量（2026-10-17 第十五批）

待办事项 `todo_list`（LIFE/LOCAL、排序 629、V77）已完成并上线：Room 本地标题/备注、高/普通/低优先级、可选截止日期、完成/重开、逾期与今日统计、搜索、三种状态筛选、四种排序、删除/清除已完成确认、500 项上限和 Unicode 码点预算；搜索/筛选/排序写入 SavedState。2 倍字体和软键盘下编辑对话框仍可滚动，保存/取消固定在键盘上方。参考 GPL-3.0 tasks/tasks 固定 commit 的任务行、完成、截止日期和分组概念，未复制源码、资源或依赖。Android 725/725、APK 24,166,476 字节、Lint 0 错误/91 警告（待办无 finding）；服务端 226/226、静态契约 86/86；模拟器验证新增/编辑/完成/重开、筛选、搜索、排序、删除/清理确认、进程重启持久化、深色/2 倍字体和键盘布局，并从真实 Room v7 升 v8 保留已有 12 条最近使用。提交 `c927f8a`、验收修复 `a4c008e`、`1a73894` 已推送；隔离候选 258/258、HTTPS 157/157，生产镜像 `sha256:dc94c9ec269859c5523e4f2a0ce773352c4e47713ed3392674dbc6185b243440`，Git/Docker 回滚标签 `before-todo-list-v77`，Flyway 77/77、官网/后台/健康均 200、五容器 healthy。详见 `../docs/TODO_LIST.md`。

## 上一轮增量（2026-10-17 第十四批）

画板 `drawing_board`（LIFE/LOCAL、排序 628、V76）已完成并上线：3:4 手绘画布、8 色画笔、1–24dp 粗细、白色橡皮擦、30 步撤销重做、清空可撤销、5×5 无障碍网格绘制、最大 80 笔/8000 采样点、保存状态恢复、1200×1600 PNG 导出、MediaStore/SAF 保存与 FileProvider 分享。参考 Apache-2.0 akshay2211/DrawBox 固定 commit 的路径/历史/导出概念，未复制源码或引入依赖。审查后补齐受控缓存预渲染、不删除 SAF 用户文件、页面离开取消分享、手势取消清理、PNG 圆角一致性和可访问控件。Android 712/712、APK 24,107,533 字节、Lint 0 错误/88 警告（新模块无 finding）；服务端 226/226、静态契约 75/75；Android 16 模拟器验证笔画、换色、橡皮擦、撤销重做、网格、深色/2 倍字体、1200×1600 PNG 保存和分享面板。提交 `da08e8d` 已推送；隔离候选 247/247、HTTPS 146/146，生产镜像 `sha256:f017298bc56460ec4cac02db37c15b8042185b4f87a705421db59b0adf98a371`，Git/Docker 回滚标签 `before-drawing-board-v76`，Flyway 76/76、官网/后台/健康均 200、五容器 healthy。详见 `../docs/DRAWING_BOARD.md`。

## 上一轮增量（2026-10-17 第十三批）

做个决定 `decision_wheel`（LIFE/LOCAL、排序 627、V75）已完成并上线：1–12 个可编辑选项、1–10 权重、SecureRandom 按权重真实抽签、顶部指针精确落点、4–6 圈缓动、可拖动转盘、三组快捷主题、不连续重复、最近 30 条历史、复制/分享、触感开关和完整 SavedState。参考 Apache-2.0 wangzhishou/OneBox 固定 commit，未复制源码或资源。只读审查后修复了条件概率文案、旋转与结果可见性恢复、拖拽/动画取消竞态、窄扇区编号及语言资源刷新。Android 699/699、APK 24,101,642 字节、Lint 0 错误/87 警告（新模块无 finding）；服务端 226/226、静态契约 79/79；Android 16 模拟器验证中文搜索、权重与基础占比、抽取结果、横竖屏角度恢复、深色和 2 倍字体。提交 `58fbc1c`、`49ff54d` 已推送；隔离候选 251/251、HTTPS 150/150，生产镜像 `sha256:b4420c6627f7589083d98a3a387fc3ff73b121b6073820db844e48ab4c00babb`，Git/Docker 回滚标签 `before-decision-wheel-v75`，Flyway 75/75、官网/后台/健康均 200、五容器 healthy。详见 `../docs/DECISION_WHEEL.md`。

## 上一轮增量（2026-10-17 第十二批）

时间屏幕 `time_screen`（LIFE/LOCAL、排序 626、V74）已完成并上线：12/24 小时制、秒数/日期/时区开关、按整秒或整分钟边界刷新、2–16dp 可调圆周防烧屏漂移、全屏沉浸、四秒自动隐藏控制、点按恢复、常亮与 SavedState。参考 GPL-3.0 supergillis/superdash 与 AGPL-3.0 wess09/AzurPilot 固定 commit 的本地格式化、分钟刷新和位移概念，未复制源码或引入依赖。Android 685/685、APK 24,090,598 字节、Lint 0 错误/85 既有警告；服务端 226/226、静态契约 64/64；Android 16 模拟器验证 12/24 小时、秒数、全屏/自动隐藏、退出/返回、2 倍字体和横屏，并修复 12 小时制 `PM` 拆行。提交 `5b78406` 已推送；隔离候选 239/239、HTTPS 136/136，生产镜像 `sha256:e3c6b75033c60f4d3cd9268a130e66302795f94b964558182f94df0c47a95f97`，Git/Docker 回滚标签 `before-time-screen-v74`，Flyway 74/74、官网/后台/健康均 200、五容器 healthy。详见 `../docs/TIME_SCREEN.md`。

## 上一轮增量（2026-10-17 第十一批）

手持弹幕 `handheld_banner`（LIFE/LOCAL、排序 625、V73）已完成并上线：120 码点单行输入、实时蓝黑预览、全屏沉浸系统栏隐藏、4 秒自动隐藏控制、可访问点按恢复、暂停/继续保留位置、速度/字号滑块、双向滚动、镜像、主题对比度兜底、常亮、中英快捷文案和 SavedState。参考 Apache-2.0 ronghao/AutoScrollTextView 固定 commit 的文本宽度/位移循环，以及 MIT sergiomattioli/LedMagic 的编辑后全屏流程，未复制源码、点阵资源或引入依赖。Android 全量 675/675、APK/Lint 0 错误/85 警告（新增弹幕文件无 finding）、服务端 226/226、静态契约 52/52；Android 16 模拟器已验证全屏、自动隐藏、暂停、退出系统栏恢复、镜像与 2 倍字体布局。提交 `fd15696`、`2d0d0aa` 已推送；隔离候选 227/227，使用一次性网络与临时 Redis；生产镜像 `sha256:bf6b4f6cd8775c04126c70b7ecd770031124cd0cd1e5203630b54d2aa9aa4c5e`，Git/Docker 回滚标签 `before-handheld-banner-v73`，Flyway 73/73、HTTPS 124/124、官网/后台 200、五容器 healthy。详见 `../docs/HANDHELD_BANNER.md`。

## 最新增量（2026-10-17 第十批）

记分牌 `scoreboard`（LIFE/LOCAL、排序 624、V72）已完成并上线：双队比分、1/2/3 分步长、直接修正、交换双方、下一节确认、最多 50 条精确撤销快照、单调计时开始/暂停/归零、重启墙钟补偿、保持屏幕常亮、中英资源和复制分享。参考 MIT qwqZYLqwq/badminton-scoreboard 固定 commit 的状态快照/恢复/比赛控制流程，未采用羽毛球专项规则、发球辅助、前台服务或街机视觉。Android 全量 664/664、APK/Lint 0 错误/85 警告（新增记分牌文件无 finding）、服务端 226/226、静态契约 61/61；Android 16 模拟器验证 3 分步长 3:6、下一节归零与撤销恢复、暂停计时、返回后状态保留、历史/分享/重置确认及 1.5×/2× 字体布局。V72 隔离使用临时 Redis、默认 bridge 发布端口、独立数据库后端网络和显式非秘密环境，禁用外部集成路径，候选 236/236。提交 `85244cb`、`095f09f`、`1062327` 已推送；生产镜像 `sha256:c9e556cf02fa0107da0cdf1f4046bb2da61c2962994f3c23aa56a8f3570bcbff`，Git/Docker 回滚标签 `before-scoreboard-v72`，Flyway 72/72、HTTPS 133/133、官网/后台 200、五容器 healthy。详见 `../docs/SCOREBOARD.md`。

## 最新增量（2026-10-17 第九批）

工作性价比 `work_value`（CALC/LOCAL、排序 58、V71）已完成并上线：月薪/发薪月数/奖金福利/工作支出、工作天数/到岗比例/在岗工时/无薪休息/通勤、年假与节假日天数；输出名义时薪、计入通勤和支出的真实时薪、年度现金/成本/净值、通勤占比及每 100 元耗时。提供双休/单休/大小周/远程预设、SavedState、范围校验和复制分享。参考 MIT Zippland/worth-calculator 固定 commit，未采用 PPP 和学历/城市等主观系数。Android 645/645、APK/Lint 0 错误/80 既有警告，服务端 226/226；Android 16 模拟器验证标准双休 60/53.33 时薪与远程 60 时薪、完整明细和分享区域。隔离候选 163/163、生产 HTTPS 105/105，生产镜像 `sha256:3e15e335d0c50ae3f7bf16f71fef956473f0055c65ae4bcce38ff4dca01f8082`，回滚标签 `before-work-value-v71`，Flyway 71/71、五容器 healthy、官网/后台 200。详见 `../docs/WORK_VALUE.md`。下一步开发记分牌 `scoreboard`（V72）。

## 最新增量（2026-10-17 第八批）

万年历 `lunar_calendar`（LIFE/LOCAL、排序 623、V70）已完成并上线：固定六周周一开头月历、前后月补位与选择、1900–2100 边界、北京时间今天与午夜自动刷新、农历/闰月、干支、生肖、节气、节日、每日宜忌、无障碍日期描述、SavedState、月份切换日号收敛、重试和复制分享。使用 MIT lunar-java 1.7.7，参考 kizitonwose/Calendar 固定 commit 的月份/日期位置建模但未新增依赖。Android 634/634、APK/Lint 0 错误/80 既有警告，服务端 226/226；Android 16 模拟器验证 2026 年 10 月网格、重阳节详情、11 月切换和 11 日选择。提交 `6578844`、验收脚本 `8ca2efc` 已推送；隔离候选 189/189、HTTPS 130/130，生产镜像 `sha256:c2bda17d…`，回滚标签 `before-calendar-v70`，Flyway 70/70、五容器 healthy、官网/后台 200。详见 `../docs/LUNAR_CALENDAR.md`。

## 最新增量（2026-10-17 第七批）

节日倒数 `holiday_countdown`（LIFE/LOCAL、排序 622、V69）已完成并上线：22 个公历/农历/节气节日、下一次发生日、当天语义、未来三次、北京时间午夜倒计时、农历/闰月显示、全部/传统/公历筛选、选择与 SavedState、整秒生命周期 ticker、重试和复制分享。使用 MIT 许可的 lunar-java 1.7.7 并打包许可证；官方法定放假/调休明确不支持且不推算。参考 6tail/lunar-java、mark216tw/anniversary-countdown-android、NateScarlet/holiday-cn 固定源码。Android 621/621、APK/Lint 0 错误/80 既有警告，服务端 226/226；Android 16 模拟器验证重阳节默认倒计时、22/11 项筛选、明天倒计时、行选择和未来三次。提交 `2425c25`、验收脚本 `3a2a59b` 已推送；隔离候选 197/197、HTTPS 127/127，生产镜像 `sha256:beb5d799…`，回滚标签 `before-holiday-v69`，Flyway 69/69、五容器 healthy、官网/后台 200。详见 `../docs/HOLIDAY_COUNTDOWN.md`。下一步继续路线图新工具。

## 最新增量（2026-10-17 第六批）

世界时间 `world_clock`（LIFE/LOCAL、排序 621、V68）已完成并上线：设备时区、23 个其他城市、635 个系统时区中英文检索、常用优先、增删/重复/上限反馈、12/24 小时制、日期线 ±2 天差、秒级偏移、DST/下一次偏移变更、SavedState 与整秒生命周期 ticker，以及复制/分享。参考 depau/wearos-world-clock-tile、PranshulGG/ClockMaster、jclllycf/best-mates-world-clock 的固定源码 commit；未复制 GPL 文件。Android 604/604、APK/Lint 0 错误/80 既有警告且新文件无 finding，服务端 226/226；Android 16 模拟器验证中文日期、12 小时制、635 项搜索、添加东京、重开恢复和删除。提交 `7c740ec`、验收脚本 `0afe068` 已推送；隔离候选 `186/186`、HTTPS `115/115`，生产镜像 `sha256:9f72d0f3…`，回滚标签 `before-world-clock-v68`，Flyway `68/68`、五容器 healthy、官网/后台 200。详见 `../docs/WORLD_CLOCK.md`。下一步继续路线图下一项，不等待重复“继续”。

## 最新增量（2026-10-17 第五批）

圆角处理已上线：集成到 `image_compress`，支持 0–50% 短边透明圆角；PNG/WebP 保留透明角，JPEG 白底。Android 582/582、Lint 0 错误/81 警告，服务端 226/226、隔离候选 188/188。提交 `1442650` 已推送，生产镜像 `sha256:b410b147…`，回滚标签 `before-rounded-v62`，Flyway 62/62、HTTPS 117/117、五个容器 healthy。真机像素回读/TalkBack 未验收。

马赛克处理已完成并上线：`image_compress` 增加 0–50% 短边像素块，无插值缩小/放大，在缩放后、圆角前执行；V63 只在未人工编辑的目录上追加 mosaic 关键词。Android 583/583、Lint 0 错误/81 警告，服务端 226/226、隔离候选 188/188；提交 `90a9c02` 已推送，生产镜像 `sha256:35d8f661…`，回滚标签 `before-mosaic-v63`，Flyway 63/63、HTTPS 117/117、五个容器 healthy。

模糊处理已完成并上线：`image_compress` 增加 0–20% 双线性缩小/放大柔化，位于缩放后、马赛克前；V64 目录关键词仅在未人工编辑时更新。Android 584/584、Lint 0 错误/81 警告，服务端 226/226、隔离候选 188/188；提交 `53e1d90` 已推送，生产镜像 `sha256:f5794cec…`，回滚标签 `before-blur-v64`，Flyway 64/64、HTTPS 117/117、五个容器 healthy。

图片滤镜已完成并上线：原色/灰度/暖色/冷色/高对比五种 ColorMatrix 预设，在圆角/编码前执行；V65 目录关键词仅在未人工编辑时更新。Android 584/584、Lint 0 错误/81 警告，服务端 226/226、隔离候选 188/188；提交 `859a71c` 已推送，生产镜像 `sha256:9b98c83a…`，回滚标签 `before-filter-v65`，Flyway 65/65、HTTPS 117/117、五个容器 healthy。

旋转功能已完成并上线：`image_compress` 增加 0/90/180/270°，90/270° 交换输出尺寸，结果元数据读取实际位图；V66 仅更新未人工编辑目录。Android 585/585、Lint 0 错误/81 警告，服务端 226/226、隔离候选 188/188；提交 `76f602e` 已推送，生产镜像 `sha256:6f2c4fe6…`，回滚标签 `before-rotation-v66`，Flyway 66/66、HTTPS 117/117、五个容器 healthy。

居中裁剪已完成并上线：`image_compress` 支持原比例、1:1、16:9、9:16、4:3、3:4，在旋转后、缩放前执行；V67 仅在未人工编辑时更新关键词。Android 586/586、Lint 0 错误/81 警告，服务端 226/226、隔离候选 188/188；提交 `aa50b59` 已推送，生产镜像 `sha256:9c1d0eb2…`，回滚标签 `before-crop-v67`，Flyway 67/67、HTTPS 117/117、五个容器 healthy。

## 最新增量（2026-10-17 第四批）

图片镜像已完成并上线：集成到 `image_compress`，提供不翻转/水平/垂直/双向选项，顺序为 EXIF 校正→镜像→缩放→PNG/JPEG/WebP 编码；V61 在描述仍为原种子时追加镜像关键词并保留人工编辑。轴缩放因子已提取为纯函数并有 4 模式回归；Android 581/581、APK/Lint 0 错误/81 警告，服务端 226/226、隔离候选 188/188；提交 `b4d8bea` 已推送，生产镜像 `sha256:311daf4b…`，回滚标签 `before-mirror-v61`，Flyway 61/61、HTTPS 117/117、五个容器 healthy。设备像素回读/TalkBack仍未验收。详见 `../docs/IMAGE_COMPRESS.md`。

## 最新增量（2026-10-17 第三批，优先于下方历史记录）

Wave 1 batch 13 `pure_color_image` 已完成并上线：Android 支持 `#RGB/#RRGGBB/#RRGGBBAA`、1–4096 边长与 800 万像素/堆预算、透明棋盘格预览、PNG、MediaStore/文档保存和 FileProvider 分享。Android 580/580、APK/Lint 0 错误/81 既有警告，服务端 226/226、隔离候选 188/188；模拟器实测 1080×1080 半透明蓝 `(22,120,255,128)`、保存和分享成功。提交 `1b60f6d` 已推送，生产镜像 `sha256:f0bb7458…`，回滚标签 `before-pure-color-v60`，Flyway 60/60、HTTPS 117/117、五个容器 healthy。真机/TalkBack/低内存/旧系统文件选择器未验收。详见 `../docs/PURE_COLOR_IMAGE.md` 与 `../docs/PURE_COLOR_IMAGE_ACCEPTANCE_2026-10-17.md`。下一步按 ROADMAP 继续独立单项。

## 最新增量（2026-10-17 第二批，优先于下方历史记录）

Wave 1 batch 12 `image_base64` 已完成并上线：Android 图片/Data URL 双向转换、magic bytes 校验、PNG/JPEG/WebP/GIF 有界 EXIF 预览、标准/URL-safe/76 字符换行、复制、系统文档保存与 FileProvider 分享；V59 已上线。独立复核的内存峰值、导出竞态、旧保存/分享任务和超长粘贴问题已修复。Android 572/572、APK/Lint 0 错误/81 既有警告；服务端 226/226，隔离候选 185/185，生产镜像 `sha256:369b3672…`，回滚标签 `before-image-base64-v59`，Flyway 59/59、HTTPS 114/114、五个容器 healthy。Android 16 模拟器已验证编解码、选项、复制、保存和分享；真机/TalkBack/低内存/全部 codec 未验收。详见 `../docs/IMAGE_BASE64.md` 与 `../docs/IMAGE_BASE64_ACCEPTANCE_2026-10-17.md`。下一步按 ROADMAP 继续独立单项，不等待重复“继续”。

## 最新增量（2026-10-17，优先于下方历史记录）

Wave 1 batch 11 `color_picker` 已完成并上线：Android 560/560、APK/Lint 0 错误/81 既有警告；服务端 226/226，隔离候选 608/608；提交 `237afad` 已推送，生产 API `sha256:db4e1cc9…` 使用回滚标签 `before-color-picker-v58`，Flyway 58/58、HTTPS 107/107、五个容器 healthy。Android 16 模拟器已验证透明 PNG、90° EXIF JPEG、真实 RGB16 PNG 与边缘取样、错误恢复、复制/微调和 360dp/1.5 倍字体/深色滚动布局。真机、TalkBack 与低端硬件耗时未验收；详见 `../docs/COLOR_PICKER_ACCEPTANCE_2026-10-17.md`。接下来按 ROADMAP 剩余图片/设备/便捷与 Wave 6 项继续单项实现，不等待用户重复回复“继续”。

## 最新增量（2026-10-15，优先于下方历史记录）

中断的汉字转拼音已完成，提交 `7ce8905` 已推送 GitHub 并部署生产：V56、Android 513/513（拼音 38）、服务端 226/226、隔离候选 309/309、线上 HTTPS 64/64，5 个生产容器 healthy。生产 API `sha256:f63bea6c…`，回滚标签 `before-pinyin-v56`；独立候选资源已清理。用户真机安装、TalkBack 和低端性能未验收。主线程与各子智能体分工、修复和发布证据见 `../docs/PINYIN_ACCEPTANCE_2026-10-14.md`，此前中断快照 `PROJECT_STATE_2026-10-14.md` 仅为历史。按用户持续授权继续逐项完善工具；每次只推进一个路线图任务，当前下一项为简繁互转（OpenCC 源码查阅、原生离线实现、V57 目录与后台统计联动）。

## 最新状态（2026-10-05，优先于下方历史记录）

用户明确要求停止真机验收，直接进入下一步；不要继续等手机或重复征求确认。Phase 2 功能范围完成，设备/第三方应用验证延后到发布，仍保持待验证记录。此前手机 stay_on_while_plugged_in 从 0 临时改成 7；未来手机重新连接时恢复为 0，本轮未连接/操作手机。

Phase 3 功能已完成：注册登录/JWT、V19 哈希刷新会话与 Keystore/DataStore 持久恢复、PUT /auth/me 昵称编辑、V20 收藏/最近/设置同步。Android Room v7 与 4→5→6→7 保留数据迁移，独立默认关闭开关和账号分区、LWW tombstone/outbox/游标；主题、语言、列数、启动页已接实际 UI。最终 Server 40、Android 170、实际认证 API 35 / 同步 API 37、主机 SQLite 30 项通过，Android test/assemble/lint 成功（最终 1 分 24 秒），Lint 0 错误/28 警告。本地服务 schema v20，目录仍 12 项。客户端同账号续期保持身份，401 me 立即清账号，避免刷新取消自身同步；匿名设置不上传，隐私开关不云同步。资料邮箱改动/头像上传、密码重置、设备会话列表、系统语言选择器双向桥接未实现；设备/Keystore/两台 Android 验收按用户要求延期，不能声称真机验收完成。具体边界 docs/AUTH.md / docs/CLOUD_SYNC.md。

用户明确要求没有重要阻塞不要停，普通路线图任务完成后继续下一项，不要发送 final 后等“继续”。Phase 4 Device & Network 八项全部完成：设备信息(V21)、存储(V22)、电池(V23)、传感器(V24)、指南针(V25)、水平仪(V26)、Ping(V27)、HTTP 状态(V28)；最终 186 项 Android 测试、assembleDebug、lintDebug（0 错误/29 警告，含 OkHttp 3.14.9 版本提示）通过，本地目录 20 项。Phase 5 进行中：Admin auth 完成（V29、独立管理员 JWT、限流、独立事务审计、引导变量、后台登录页/守卫/退出），Tool CRUD 完成（管理端列表/详情/新增/编辑/上下线与维护/软删除、只读分类选项、公开目录联动、tool 审计；无数据库结构变更），Category CRUD 完成（分类列表/新增/编辑/启停/软删除、在用保护 409/30007、停用分类隐藏工具、category 审计；无数据库结构变更），Recommendation slots 完成（V30 home_recommendation：工具或 https 链接目标、排序、启停、UTC 生效时间窗；管理端 CRUD + 审计与匿名 GET /api/v1/home/recommendations 仅返回生效项；Android 首页接入未做、不得声称 App 已生效），User management 完成（搜索/详情/收藏最近计数/掩码会话列表/同步键列表，停用即撤销全部刷新会话并让访问令牌立刻失效，user 审计；无数据库结构变更），Announcements 完成（V31 announcement：级别/草稿与发布/UTC 时间窗；管理端 CRUD + 审计与匿名 GET /api/v1/home/announcements 最多 5 条纯文本），Feedback 完成（V32 user_feedback：用户提交与 /feedback/my、管理员关键词/状态检索、处理与回复、feedback 审计；Android 端接入未做），Operation logs 完成（只读 GET /api/v1/admin/logs：模块/结果/关键字过滤、分页、管理员名外连接）。Phase 5 全部八项完成（Admin auth、Tool CRUD、Category CRUD、Recommendation slots、User management、Announcements、Feedback、Operation logs）。Phase 6 已开始：MinIO 完成（MinIO SDK 8.5.17、默认关闭的存储开关、懒建桶、有界上传+服务端 SHA-256、预签名下载、删除、仅在启用时注册的健康指标、管理员 /api/v1/admin/storage 接口与 storage 审计；无数据库结构变更）。当前验证基线：Server 99/99 测试、admin typecheck/build、管理员端 20/20+20/20+22/22+26/26+19/19+25/25+17/17+13/13、对象存储 23/23（真实本地 MinIO 容器，含网络内预签名下载哈希一致）。File security 已完成（上传路径 32 字节魔术字节嗅探 + 声明/检测 MIME 严格一致，拒绝二进制伪文本与主动内容类型），Quotas 已完成（V33 stored_object 记账、每所有者字节/对象数与全局字节三道写入前校验 413/40004、管理端状态暴露用量、删除释放额度；Server 107/107 测试、真实 MinIO 上 38/38 存储校验）。Rate limits 已完成（Redis 固定窗口 + 内存兜底、身份分桶、登录端点更严格、统一 429/10007 与 X-RateLimit/Retry-After 头；Server 116/116 测试与 61/61 限流校验，随后全量回归 13/20/22/26/19/25/17/38/20 全部通过，环境已恢复默认并清空临时数据）。Phase 6 中不依赖外部服务的部分全部完成；OCR、PDF、AI 需要外部服务商与凭据决策（已向用户提问，等待选择）；Web tool release acceptance 仍在待验收清单。手机 stay_on_while_plugged_in=7 的恢复仍待手机重新连接时执行（当前未连接）。混合工作区未提交/推送，不能擅自全量提交；临时目录 .tmp/phase3 待最终无用文件清理。所有可控文件保存在 D 盘。

## 开场消息（复制粘贴）

请接手「沧烁工具箱」项目开发。

- 项目目录：D:\codeStudy\code\cangshuo-tool（远端 https://github.com/zhz520/cangshuo-tool，主分支 main）
- 先按顺序阅读：
  1. toolbox-vibe-spec/AGENTS.md（开发约定）
  2. toolbox-vibe-spec/ROADMAP.md（进度与下一步）
  3. toolbox-vibe-spec/PROJECT_SPEC.md（完整设计）
  4. 涉及 Android UI 时再读 docs/ANDROID_UI_SPEC.md，视觉参考 stitch_cangshuo_tool_android_ui_redesign/
  5. 开发或修复工具时读 docs/TOOL_DEVELOPMENT_GUIDE.md，阅读 GitHub 相关优秀源码，并在工具文档记录实际参考、适配与功能/边界覆盖。
- 先检查 Git 工作区，并阅读 docs/DEVELOPMENT_REVIEW_2026-10-04.md 的修复进度；优先完成尚未解决的审查问题，再从 ROADMAP 中第一个未完成任务开始。每次只做一个任务，先说明最小计划和文件范围，再实施。
- 完成后执行适用的构建/Lint，更新 ROADMAP 与对应 docs，提交信息使用 feat:/fix:/refactor:/test:/chore: 前缀。
- 新建文件保存到 D 盘：项目文件放项目目录，独立交付物放 D:\CodexData\outputs，临时文件放 D:\CodexData\temp。
- 用中文交流。

当前进度（2026-10-04 工作区快照；先以本机 Git 和文件为准）：
- Phase 1 已完成：首页、工具注册、Server GET /tools、计算器、本地搜索、本地收藏、本地最近使用；设备运行验证仍待做。
- Phase 2 新增单位转换、时间戳、UUID、Base64、URL 编解码、Hash、JSON、文本工具、二维码、图片压缩，共 10 个工具；加上计算器共注册 11 个。新增代码、文档和 V4–V14 迁移仍未提交，远端旧提交不能代表本机工作区。
- 已完成代码审查并写入 docs/DEVELOPMENT_REVIEW_2026-10-04.md。单位转换的默认交换、数值规模限制、完整科学计数法及单位标签已完成代码修复；最终 Android 构建/Lint 成功，0 错误、32 警告；未新增或运行自动化测试，设备与回归验收仍待做。细节见该工具文档和报告修复进度。
- 图片压缩已按实际 GitHub Compressor 源码参考完成代码修复：大图预算、文件输出、旧任务归属、真实预览、API 26–28 系统文件保存、后台分享及共享加载/中英反馈；最终离线 Android 构建/Lint 成功（65 秒），0 错误、27 警告；设备、导出与自动化回归验证仍待做。细节见 docs/IMAGE_COMPRESS.md。
- 用户要求已写入两处 AGENTS 和 docs/TOOL_DEVELOPMENT_GUIDE.md：以后每个工具开发/修复要阅读相关 GitHub 优秀源码，功能与边界完整，并写明来源、采用思路和实际验证，不能只列项目名称。
- 二维码已按 ZXing 3.5.3 与 JourneyApps 实际源码完成本轮代码修复：后台 URI 读图/采样/识别、内存和读取预算、取消与旧任务归属、反色重试、Wi-Fi 字段转义/隐藏网络、四模块留白、共享加载和中英反馈。最终离线 Android 构建/Lint 成功（54 秒），0 错误、25 警告；临时目录已清理，未运行自动化/样例/设备验收，见 docs/QR_TOOL.md。结果导出随后补齐（见下），相机/条码/专用类型仍未完成，路线图不勾选。
- 文本工具已按 RE2/J 1.8 与 IT-Tools 实际源码完成本轮代码修复：后台串行处理、编译/输出预算、取消/版本归属、分组替换与选项、Unicode/换行统计、长数字排序、统一加载和中英反馈。最终离线 Android 构建/Lint 成功（58 秒），0 错误、27 警告；文本源码/新增资源无 Lint 问题，75 个中英 key 对齐，许可证已打包，临时目录已清理；未运行样例、自动化或设备验收，语法子集和协作取消边界见 docs/TEXT_TOOLS.md。
- JSON 已完成本轮代码修复：参考实际 CyberChef/IT-Tools 与 Lottie reader 源码及 RFC，使用有界严格扫描，保留根字符串引号、数字原文和全部重复成员；补齐严格转义/两种反转义、后台处理、取消/版本、资源预算、错误位置、统一加载和中英提示。三轮离线 Android 构建/Lint 均成功，最终 55 秒、0 错误、25 警告；JSON 源码/相关资源无 Lint 问题，54 个中英 key 对齐，本轮临时目录已清理；未运行业务样例、自动化或设备验收，Unicode 额外限制与其余边界见 docs/JSON_TOOL.md。
- URL 编解码已完成本轮代码修复：参考 WHATWG URL Standard 与实际 jsdom/whatwg-url、CyberChef、IT-Tools 源码，移除 `java.net.URLEncoder/URLDecoder` 表单语义混用，改为组件/URI 整体/表单值三类型的严格单层百分号编解码；修复加号往返与组件字符集，加入严格 %HH/UTF-8 校验、错误位置、后台互斥、取消/版本、预算、预览截断提示、共享加载和中英反馈。三轮离线构建/Lint 成功，最终完整构建 83 秒、增量 15 秒，0 错误、24 条警告（本轮开始 25 条），44 个中英 key 对齐，临时目录已清理；V8 不变，未运行自动化/样例/设备验收，见 docs/URL_CODEC.md。
- 时间戳已完成严格日期解析修复：参考实际 IT-Tools/CyberChef 与 OpenJDK ResolverStyle/DateTimeFormatter 源码，改用 `ResolverStyle.STRICT` + `uuuu` 模式，`2026-02-30` 等不存在日期返回错误；新增 InvalidDate 分类与中英提示、1900–3000 年份范围、夏令时缺口拒绝与重叠策略。JBR 验证了严格/宽松行为、闰年和 DST；离线构建/Lint 成功（1 分 26 秒），0 错误、24 警告，30 个中英 key 对齐，临时目录已清理；未运行自动化/设备验收，见 docs/TIMESTAMP.md。
- UUID 分层已修复：移除 ViewModel 直接调用 `UUID.randomUUID()` 与字符串拼接，生成/格式化统一经 GenerateUuidUseCase → UuidRepository（generateRaw/format），初始状态按 SavedState 选项格式化。离线构建/Lint 成功（1 分 16 秒），0 错误、24 警告，16 个中英 key 对齐；JBR 验证 10,000 个 v4 UUID 全部合法且无重复，临时目录已清理；未运行自动化/设备验收，见 docs/UUID_GENERATOR.md。
- Base64 非 UTF-8 策略已修复：解码字节优先、文本严格 UTF-8；非法字节不再替换为 U+FFFD，显示无效字节数与首个位置，禁用文本复制/互换；解码后始终提供无损 Hex 视图（预览 8000 字符、复制完整）。参考 CyberChef/IT-Tools 实际源码，离线构建/Lint 成功（1 分 29 秒，文案重跑 1 分 40 秒，最终增量 1 分 10 秒），0 错误、24 警告，25 个中英 key 对齐；JBR 对最终编译类复验 20/20 通过，临时目录已清理；未运行自动化/设备验收，见 docs/BASE64_CODEC.md。
- 二维码多语言收尾已完成：WPA/WPA2、WEP 与 L/M/Q/H 标签移入中英资源（协议原文、两端同值），领域枚举不再携带展示字符串；QR feature 未再发现用户可见硬编码字面量，`qr_*` 中英各 58 个 key 对齐；离线构建/Lint 成功（1 分 22 秒），0 错误、24 警告。此前 QrColorStyle.displayName 与英文分享文案问题在当前代码中已不存在。
- 页面切换动画已统一：`core/ui/ToolboxMotion.kt` 提供工具/搜索前进后退（进入 240ms、退出 180ms 的滑动+淡化）与底部标签 180ms 交叉淡化；HomeRoute/HomeScreen 已接入，页面不再自定义动画；显式加入 Compose Animation 依赖（BOM 管理）。离线构建/Lint 成功（2 分 48 秒，全量），0 错误、24 警告；关闭系统动画、TalkBack、快速连续切换与低端设备验收仍待执行，见 docs/ANDROID_UI_SPEC.md。
- 页面特性宣传语已移除：11 个工具页的“本地处理/无需登录或联网”标签、首页“本地优先”标语、卡片运行模式徽标全部删除；搜索/收藏/我的/二维码说明改为中性功能描述；工具描述去掉“本地计算/离线处理/保护隐私”等收尾措辞，URL 工具加载/错误文案改为中性。`ToolMode` 数据保留但不再在界面展示。Android UI 规范已新增“禁止特性宣传语”规则；离线构建/Lint 成功（完整 2 分钟，最终增量 1 分 3 秒），0 错误、24 警告，无残留引用；服务端描述已通过下述 V14 统一；设备/语言验收仍待执行，见 docs/ANDROID_UI_SPEC.md。
- 服务端默认描述已对齐：新增 V14，按 code 和二进制精确原始种子值替换 11 条描述，保留人工编辑及其他目录配置；V1–V13 未改。Server 镜像构建成功（Maven 3.448 秒、测试跳过），Flyway 实际执行 V14（27ms），schema v14、14 条历史全部 success=1、旧校验值不变。会话临时表验证自定义/尾空格保留和关闭/软删除配置不变；直连/代理目录一致，11 条说明与 Android 中文资源相同，其他公开字段未变，五个容器健康；本轮临时文件清理，未新增持久化自动测试或执行 Android/设备验收。见 docs/TOOL_CATALOG.md 和 docs/DATABASE.md。
- 二维码结果导出已补齐：阅读 ZXing 3.5.3 QRCodeEncoder/EncodeActivity 实际源码，新增分层导出 Repository/UseCase、512/1024/2048 PNG、整数网格和留白、API 29+ 相册/API 26–28 系统文件保存、FileProvider 分享、识别原文分享与重新生成；导出快照/互斥、预算/失败清理、共享加载与双语反馈。两轮离线构建/Lint 成功（93 秒、最终 81 秒），0 错误、24 既有警告，79 个中英 QR 字符串 key 对齐；JBR 编译布局 120 例和 Java2D PNG/ZXing 144 次往返通过，不覆盖 Android Canvas/相册/SAF/分享接收方，设备验收仍待做，未新增持久化自动测试；临时脚本清理。见 docs/QR_TOOL.md。
- 二维码相机扫描与条码支持已补齐：引入 CameraX 1.6.2、按需 CAMERA 权限（工具打开不拦截）、无相机/权限被拒/启动失败三态、生命周期绑定、手电筒、Y 平面去 stride、90°/270° 转正与半转复用、KEEP_ONLY_LATEST 背压；相机与相册共用 13 种二维码/条码格式（相册解析由 QRCodeReader 改为 MultiFormatReader）。离线构建/Lint 成功（全量 2 分 20 秒、确认 1 分 32 秒），0 错误、24 既有警告、0 hint，93 个中英 QR key 对齐且引用齐全，二维码源码无 Lint 问题；JBR 验证 2 个 Y 平面样例、6 个方向样例、8 次条码往返、13 种格式清单通过，不覆盖 Android ImageProxy/CameraX 绑定/权限对话框/预览/手电筒与真机识别率；APK 增至 17,359,669 字节（CameraX 原生库约 +4.6 MB）。见 docs/QR_TOOL.md。
- 二维码专用内容类型已补齐：生成侧现有 7 类内容（文本/网址、Wi-Fi、电话、邮箱、短信、联系人、日历事件）。电话/短信使用 `tel:`/`SMSTO:`，邮箱使用 `mailto:`，联系人使用 MECARD（按 ZXing 3.5.3 `MECARDContactEncoder` 的字段顺序与转义规则，参考同时阅读了 `ContactEncoder`/`VCardContactEncoder`），事件使用 iCalendar VEVENT 本地浮动时间；日期按 `uuuu-MM-dd`、时间按 `HH:mm` STRICT 解析，结束时间可留空但必须晚于开始。字段上限 256、短信正文 500、日期 10、时间 5，全部通过 `QrInputPolicy` 校验与 `hasContent` 空状态判断，表单值写入 SavedState。离线构建/Lint 成功（首轮修复 1 条 ModifierParameter 与 2 条 TypographyDashes，最终 1 分 32 秒），0 错误、24 既有警告、0 hint，129 个中英 QR key 对齐；JBR 验证 43 项（7 种类型、格式拒绝、MECARD/iCalendar 转义、STRICT 日期时间、结束早于开始、真实 QR 往返）全部通过，未覆盖真机与第三方扫码器导入行为。见 docs/QR_TOOL.md。
- 二维码多码识别（相册）已补齐：解析页新增“识别多个二维码”开关（SavedState 恢复），开启后由 `QrMultiDecoder` 调用 ZXing `QRCodeMultiReader` 检测同图全部二维码，按检测顺序去重；1 个结果仍走原结果卡，2 个及以上展示列表并支持逐条复制、复制全部与分享全部（换行连接）；多码未命中时回退单码路径，所以图里只有条码时仍可识别。多码只覆盖二维码，相机仍只取第一个结果。离线构建/Lint 成功（1 分 19 秒），0 错误、24 既有警告、0 hint，135 个中英 QR key 对齐；JBR 验证 9 项（三码、单码、去重、空白、纯条码回退、混合图只取二维码、共享提示、极小帧）全部通过；未覆盖真机耗时与布局。见 docs/QR_TOOL.md。
- 相机多码收集已补齐：多码开关打开时 `QrCameraAnalyzer` 走 collect 分支，用 `QrCameraCollector`（去重、单次上限 50）持续回调新载荷；ViewModel 用内存态 `cameraResults` 保存会话，预览显示已收集数量，按钮文案改为“完成”，停止时 1 个结果进原结果卡、2 个及以上进结果列表，空会话只关闭预览并保留旧结果。扫描期间多码开关禁用，切换开关会兜底结束会话。离线构建/Lint 成功（1 分 16 秒），0 错误、24 既有警告、0 hint，138 个中英 QR key 对齐；JBR 验证 72 项收集器用例（去重、空载荷、自定义与默认上限、Unicode 与协议载荷）全部通过；未覆盖真机收集节奏与布局。见 docs/QR_TOOL.md。
- 识别结果符号名称已补齐：新增 `QrSymbology` 与 `QrDecodeEntry(text, symbology)`，`QrBarcodeFormats.symbologyOf` 统一映射 ZXing `barcodeFormat`（13 种格式 + `UNKNOWN` 兜底）；相机分析器、单码与多码路径都返回条目，UiState 改为 `decodeEntry`/`decodeEntries`，相机会话同样保存条目；结果卡与多码列表显示格式标签，协议名中英同值、仅“未知格式”翻译。离线构建/Lint 成功（1 分 21 秒），0 错误、24 既有警告、0 hint，152 个中英 QR key 对齐；JBR 验证 27 项（13 种映射、null/MAXICODE/RSS_14 回退、QR/Code 128/Code 39/EAN-13 真实解码标注、多码路径）全部通过；未覆盖真机布局与真实样本核对。见 docs/QR_TOOL.md。
- Phase 2 剩余收尾：V4–V13 数据库迁移与 GET /tools 运行时联调已于 2026-10-04 完成；随后 V14 完成默认描述统一（当前 schema v14、14 条历史全部 `success=1`）。二维码相机、13 种符号体系、专用内容类型、相册多码、相机收集、符号名称展示、邮件头部字段与事件纪要已补齐（mailto 支持 RFC 6068 subject/body 百分号编码，VEVENT 支持多行 DESCRIPTION）；线性条码分带多码检测已补齐（QrBandDecoder，三条重叠横向分带，QR 多码未命中时启用，成本接近一次全图解码）；竖向分带与混合多码已补齐（竖带先转置；QR 多码与分带结果按文本合并）；WEB 模式契约层已落地（ToolMode/DTO/Server 允许值/V15 CHECK，Docker 重建 server 镜像后 Flyway 实际执行 V15（00:00.133s），schema 升到 v15、15 条历史全部 success=1，CHECK 约束已含 WEB，目录仍为 11 个工具、0 个 WEB）；WebView 容器与域名白名单已实现（core/ui/ToolboxWebView.kt、buildConfig 基础地址、debug 明文白名单，构建/Lint 0 错误 24 警告），客户端 WEB 打开路径已接线（ToolDetailPage 按 ToolMode.WEB 走 ToolboxWebScreen），官网已托管 /tools/qr/ 网页版占位页（本地 200，admin 镜像已重建），首个 WEB 工具 qr_studio 已登记（V16 / schema v16，API total=12 且含 mode=WEB，/tools/qr_studio/ 返回 200）；页面真实生成功能已完成（文本、Wi-Fi 含 WPA3/EAP、vCard 多值，PNG/JPEG/SVG 导出；payload.js 经 node 测试通过，页面与资源均 200）；qr_studio 已注册进客户端 ToolRegistry（WEB，中英资源与 V16 描述一致，App 内可见并按容器打开）；目录远程合并与自动更新已接线（ToolCatalogClient + ToolRegistryStore，各入口观察共享快照，启动后台请求一次）；下一项为目录网络契约/读取预算、缓存或浏览器/真机 WebView 验收（DATABASE 的 V16 表行已补齐），之后按路线图处理扫码历史、裁剪、vCard/多值、事件时区与提醒、SVG/JPEG 等剩余缺口并推进设备验收。剩余设备、英语切换、剪贴板、相机授权与 Wi-Fi 连接、PNG 保存/分享与扫码识别率、结构化载荷在第三方应用中的导入、多码耗时与相机收集节奏、符号标签布局验收，页面动画的关闭系统动画/TalkBack/低端设备验收，以及仓库自动化回归测试缺口。完成设备验收前不要把 Phase 2 标记为全部验收通过，也不能直接进入 Auth & Sync。
- 生产域名规划：官网/后台 tool.zhzgo.cn，API toolapi.zhzgo.cn。
- 网页版工具承载规则已确定并写入 PROJECT_SPEC Decision 019、两处 AGENTS、docs/TOOL_DEVELOPMENT_GUIDE.md、Android UI 规范与部署说明：复杂或体积大的工具可放官网 `/tools/<code>/`，App 用系统 WebView（Chrome 内核）打开；`WEB` 模式、数据库 CHECK 迁移与 API/DTO 尚未实现，首个网页版工具落地时同步。
- 本地环境：Docker 数据在 D:\DockerData；Compose 项目 cangshuo-toolbox-local（server 8081、admin 8088、mysql 3307、redis 6380）。
- 2026-10-04 已启动 Docker Desktop Linux 引擎（29.8.1）并重建本地 server 镜像；当前已执行到 V14，GET /tools 与 Nginx 联调通过、五个容器健康。接手时仍以 `docker compose ps` 与本机实际状态为准。

## 使用说明

2026-10-05 最新增量（覆盖上方历史状态）：目录读取校验、Room v3 WEB 快照和应用级去重刷新已完成；启动先恢复缓存，前台 5 分钟、手动 2 秒。真实 Server schema v17（含 V17 描述修正）、目录 12 项；Android 16 验证更新、失败重启恢复纯远程探针、搜索、恢复移除探针及网页打开。浏览器验证 Unicode/文本/Wi-Fi EAP/vCard 多值、复制、容量错误和恢复。修复网页 UTF-8、旧二维码残留、版本展示、换行和旧缓存；WebView 补齐严格来源策略及分层 SAF 导出。最终 96/96 Android 测试、24 SQLite、22 网页回归通过，离线门禁 2 分 44 秒、Lint 0 错误/22 既有警告，Nginx 通过、页面 200/no-cache。手机保存、完整历史/错误/TLS 未验收；最后禁缓存 APK 因 USB 断连尚未更新安装。用户要求加快，停止追加设备场景，补验留到发布阶段，不再循环等待设备或阻塞功能开发；不要勾选完整 Phase 2。二维码工具的原生功能已全部落地：扫码历史、事件时区/提醒、手动框选识别、线性条码生成（CODE 128/39/93、EAN-13/8、UPC-A、ITF、Codabar）、密集混合多码线性补扫、PNG/JPEG/SVG 导出、邮件抄送/密送、WPA3 与企业 Wi-Fi（EAP）、MECARD/vCard 3.0 多值联系人和 qr_studio 描述修正（V17/schema v17）：141/141 Android 测试、24+12 项主机 SQLite 检查通过，构建/Lint 0 错误/22 既有警告，本地 server 已应用 V17；Phase 2 功能已全部完成，只差设备与第三方应用验收（当前手机未连接），实施前读 docs/QR_TOOL.md、docs/QR_HISTORY.md 与相关业务源码。详细范围见 docs/WEB_TOOL_ACCEPTANCE_2026-10-05.md。现有混合工作区未提交，不擅自全量提交/推送。

- Codex 桌面版：把会话工作目录设为 D:\codeStudy\code\cangshuo-tool 后，项目 AGENTS.md 会自动加载，但仍建议发一次开场消息说明当前任务。
- 同一会话内换模型：上下文自动延续，不需要特殊说明，换完后直接说「继续」即可。
- 新窗口最容易出问题的是 AI 凭记忆假设进度。第一句一定要让它先读 ROADMAP 再动手；仓库文档比任何口述摘要都新。
- 如果新会话没有本机文件访问能力，可先读 GitHub 中已提交的资料；未提交工作区不会出现在远端，需额外提供本机改动及审查/修复进度。
