# 反应力测试

`reaction_test`，OTHER / LOCAL，排序 638，目录迁移 V86。Android 使用 `ReactionRoute → ReactionViewModel → ReactionUseCases → LocalReactionRepository`；不新增权限、依赖、Room 表或业务 API。

## 源码参考（2026-10-20）

实际阅读 [Dakostu/TapIt GameActivity.java](https://github.com/Dakostu/TapIt/blob/4efbcaf94bc3cc4aa9b1eec5344719cfed9fa010/app/src/main/java/dakostu/tapit/GameActivity.java)，固定提交 `4efbcaf94bc3cc4aa9b1eec5344719cfed9fa010`；同时核查根目录 [LICENSE](https://github.com/Dakostu/TapIt/blob/4efbcaf94bc3cc4aa9b1eec5344719cfed9fa010/LICENSE) 为 GPL-2.0。借鉴随机等待、先点失败、单调时钟相减和重试的流程概念，未复制源码、资源或引入依赖。

上游在 Handler 切换视图前记录时间，点击后按固定阈值给用户排名。本实现从 Compose 提示区域第一次 draw 记录 `SystemClock.uptimeMillis()`，触摸使用 pointer-down 的 uptime 时间，而非抬起时刻；不采用人群排名。随机等待改为 SecureRandom 的 1500–4500 毫秒整数；取消任务并用代次隔离旧回调，前后台与旋转中断本轮；保留有效的前几轮而非重启 Activity。

## 功能与边界

- 3 / 5 / 10 个有效轮次，随机等待；提示出现前、低于 80ms 的输入判作抢跑，超过 3000ms 判作超时，均可重试且不占有效轮次。
- 80–3000ms（含端点）接受为有效成绩。提示 draw 前不接受成绩；多次 draw 不重置计时，多指或连续点按不会重复记分。
- 展示最快、最慢、平均、中位数、总体标准差及逐轮明细；摘要与 CSV 复制、系统分享复用共享组件。CSV 只有轮次与毫秒，不含用户输入。
- 当前测试保存在 SavedState；进程恢复不延续旧提示计时。完整测试自动保存最近 20 组，按唯一会话 ID 幂等去重；清空历史需要确认，清空后不因状态恢复重建记录。
- 历史在私有 SharedPreferences 中以不超过 32KiB 的 JSON 保存，读写通过 IO dispatcher 和 Mutex 串行执行。损坏、读写失败可重试；不把失败描述为已保存。保存失败时保留当前成绩并阻止直接丢弃；读取损坏可经确认清空恢复。
- 本轮开始前使测试区域滚入视野；等待/响应期间禁用滚动，保持屏幕常亮。不添加提示出现动画、声音或振动，减少额外时序线索；其他控件沿用原生反馈和主题色。
- 中英资源、深浅色、主题形状、最大宽度、可滚动布局、文字提示及可访问点击/键盘入口。读屏/键盘与触控成绩不直接比较。
- 切后台、锁屏或页面销毁取消当前等待和超时，不记失败；超时任务和延迟触摸由状态机拒绝重复结算。

仅供同设备练习娱乐；应用绘制时刻不等于面板物理发光时刻，设备显示、系统调度及触控延迟均会影响结果。没有校准、云排行榜、医学评估、年龄/人群分位或全屏闪烁。

## 服务端与后台

V86 只登记目录元数据，重放保留人工编辑、开关、排序、软删除与使用记录。后台继续使用既有工具管理、使用量、热门排行及 7/30/90 天统计；不会接收反应样本、失败次数或成绩历史。

`scripts/check_reaction_test_runtime.py --static` 检查双语格式参数和目录契约；候选镜像模式使用既有 strict isolation 隔离数据库、账号与 Redis 验证后台编辑、迁移重放保持、使用计数和统计排行。`verify_reaction_test_deploy.py` 只做生产 GET 和一次管理员登录，不制造生产使用量。

## 验证进度

2026-10-20 本地门禁：

- Debug / Release 单元测试各 **827/827**（124 suites）；两种 Lint 均 **0 错误 / 91 条既有警告**，新工具无 finding。曾触发 Lint UAST 内部异常，独立进程单 worker 完整重跑后通过，没有关闭规则。
- Debug APK **25,889,062** 字节，Release APK **17,526,757** 字节，AAB **16,921,555** 字节；release artifact 门禁通过，`signatureVerified=false`（未签名发行物）。
- 服务端 **226/226**；双语与目录静态契约 **14/14**，已加入 CI。API 字段和后台界面没有改动。
- Android 16 模拟器验证：搜索并打开；真实画面提示后触摸完成三轮；抢跑、3 秒超时、后台返回中断；三轮均值/中位数/标准差；私有历史文件实际落盘；CSV 复制、系统分享面板和新测试确认；**720×1600 / 2 倍字体 / 深色**下可滚动查看完整提示与操作。恢复了模拟器原字号与分辨率。
- JUnit 覆盖统计、假启动/超时、输入事件时刻、重复 draw/点击、生命周期、进程恢复、完整保存/清空与失败重试。真机触控、面板时序校准及 TalkBack 人工朗读未验收。

服务端隔离和生产验收待发布后补充，不将本地通过写成已上线。
