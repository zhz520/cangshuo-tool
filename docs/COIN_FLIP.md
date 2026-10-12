# 抛硬币

`coin_flip`，OTHER / LOCAL，排序 600。原生 Compose → CoinFlipViewModel → FlipCoinsUseCase → LocalCoinRepository；本机 SecureRandom，无登录、无额外权限、无网络请求或随机结果上传。

## 具体开源参考

查阅日期：2026-10-12。以下业务源码已实际读取；没有复制代码、素材或引入新依赖。

| 来源 | 读取内容与适配差异 |
| --- | --- |
| [joelkanyi/flip CoinSide.kt](https://github.com/joelkanyi/flip/blob/5154f58cff8cf0ead7189079eecfb6f92398400b/wear/src/main/java/com/joelkanyi/flip/data/model/CoinSide.kt)、[ViewModel](https://github.com/joelkanyi/flip/blob/5154f58cff8cf0ead7189079eecfb6f92398400b/wear/src/main/java/com/joelkanyi/flip/presentation/viewmodel/CoinFlipViewModel.kt)、[Repository](https://github.com/joelkanyi/flip/blob/5154f58cff8cf0ead7189079eecfb6f92398400b/wear/src/main/java/com/joelkanyi/flip/data/repository/FlipRepositoryImpl.kt)、[Coin.kt](https://github.com/joelkanyi/flip/blob/5154f58cff8cf0ead7189079eecfb6f92398400b/wear/src/main/java/com/joelkanyi/flip/presentation/components/Coin.kt) | 研究 SecureRandom.nextBoolean、动画阶段、计数与结果、反面文字镜像修正。参考项目 GitHub 元数据未声明许可证，仅研究思路。没有使用其四阶段长动画、持续漂浮、声音/触觉或持久统计；本项目为 480ms 可关闭有限翻面，计算前同步防连点，取消/异常都退出忙碌态，落定回调编号校验后一次提交结果。 |
| [banasiak/CoinFlip CoinFlipActivity.java](https://github.com/banasiak/CoinFlip/blob/faec956c50fa05a7299efd51e4768b1dfa90c472/mobile/src/main/java/com/banasiak/coinflip/CoinFlipActivity.java)，GPL-3.0 | 实际读取 flipCoin、计数、前后状态和渲染转移（371–457 行）。借鉴明确显示正反面与会话统计的流程；不复制其 Activity、旧式帧图、摇动监听、广告/插件资源或日志。批量与保留原始文字历史按本项目领域模型实现。 |
| [Java SecureRandom 官方文档](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html) | 使用系统提供的随机源，不以时间、固定种子、交替或当前比例强制修正结果。不将短期样本统计描述为随机性认证。 |

## 功能与边界

- 点击硬币或按钮投掷一次；批量支持 1–100 次及 3/10/50/100 预设。独立调用 nextBoolean，不强制次数相等，允许连续相同结果。
- 正反面文字各最多 20 个 Unicode 码点（表情可作为一个码点），空输入使用当前语言的默认文字。首尾空白去除；两面忽略大小写后必须不同。控制字符、双向隐藏方向字符、孤立代理项拒绝。超长编辑保留上一次输入并提示。
- 累计正/反次数与比例、当前连续同面、最长连续同面；本批次数与多数/平局来自实际结果。会话最多 100 万次，达到预算后明确要求清空。
- 最近 100 条历史保留序号、面和投掷当时的文字。默认展开最近 20 条，可展开全部保留记录。修改文字不改写历史，全会话统计不因历史裁剪丢失。
- 清空前确认，仅清结果/统计，保留文字、批量次数和动画偏好。选项通过 SavedStateHandle 保留本工具会话；原始历史、结果和统计只在内存，不承诺跨会话保存。
- 统计摘要或 CSV 可复制及系统分享。CSV 为 `number,side,result,heads_label,tails_label`，按保留记录的原始顺序，最多 100 行；明确提示保留数量和累计数量。引号/逗号按 CSV 转义，用户文字以 `= + - @` 开头时加前导单引号阻止表格公式执行，界面和历史原值保持不变。
- 中文/英语字符串一致；主题令牌和共享输入/按钮/开关/结果行/加载/分享组件。共享 ToolboxFlipCoin 以有限动画落定，不持续漂浮，反面文字不镜像；关闭动画或系统关闭动画时立即回调。

## 并发与生命周期

UI 不直接生成随机值或访问存储。Repository 在 Default dispatcher 处理，读取前校验次数并在每次循环检查取消；UseCase 校验返回条数，拒绝部分成功。ViewModel 在 launch 前同步设置 busy，提交时固定次数/文字，期间禁用编辑。

3 秒随机请求超时与异常显示本地化失败，没有虚构结果。结果先进入 pending，共享动画落定后按本次编号一次提交；重复/过期回调无效。离开前台取消计算、丢弃未落定批次并提示中断，旧任务不能覆盖之后的新结果。清空也会取消 pending，保留此前用户选项。没有日志记录用户文字、结果或敏感数据。

当前不支持硬币边缘落地、物理仿真、预测、加权概率、联网同步、定时后台投掷或直接写 CSV 文件。历史裁剪不是完整日志归档，短期多数不能作为后续结果预测。

## 前后端与验证

V52 只增加工具目录 seed，不修改旧 migration/数据库结构/API 契约。元数据和 Android 中文资源对齐。工具管理（状态、维护、排序、推荐、软删除）与 `/tools/coin_flip/usage`、热门排行榜、管理统计沿用现有业务链路；不新增上传用户投掷数据的接口。种子重放保留管理员编辑与软删除。

2026-10-12 Android 验证：Debug 单元测试 400/400 通过，本工具新增 25 项（领域 13、ViewModel 12），覆盖次数与文字校验、表情码点、连续相同结果、批次边界、历史 100 条预算、会话上限、CSV 注入中和、防连点、取消/超时/失败、过期回调隔离与设置恢复。APK 构建、Lint 通过（0 错误 / 77 警告，新增文件无发现）；中英文资源对齐，计数使用 plurals。

Android 16 模拟器实际操作：关闭动画立即落定、单次结果显示、自定义 Go/Stay 生效且历史保留投掷时文字、相同两面被拦截、批量次数 0 被拦截、100 次批量落定、统计比例与连续/最长连续、历史 CSV 复制无错误反馈、清空确认后统计与历史归零、1.5 倍字号与深色模式可正常滚动阅读。

后端隔离候选与线上 HTTPS 验收结果执行后在此补充。未在用户手机安装或做真实设备验收。
