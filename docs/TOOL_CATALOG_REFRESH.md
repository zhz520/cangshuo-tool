# 工具目录刷新策略

2026-10-05，目录缓存任务后接入。`RequestWebToolCatalogSyncUseCase` 在应用级协程中先恢复缓存，再获取完整网络目录；首页 ViewModel 发起请求并观察状态，UI 不访问网络或 Room。

## 触发与边界

- 启动自动请求一次；回到前台时检查，自动请求间隔 5 分钟，使用单调时钟，从尝试开始计时，失败也限频。
- 首页和工具页提供“更新目录”；手动请求绕过 5 分钟限制，保留 2 秒防连点。
- 启动、前台、手动请求共享一个在途任务；页面离开只取消自己的等待，应用级更新继续。
- 第一次请求先恢复有效缓存，后续请求不重复恢复；网络失败保留列表、筛选条件和旧缓存。
- 中英文同步状态与共享小型加载组件，不阻塞整个列表。
- 无后台周期任务、WorkManager、自动重试队列或目录到期删除；进程停止后，下次启动重新尝试。

## 源码参考

查阅日期：2026-10-05。

| 具体源码 | 采用思路 | 适配差异 |
| --- | --- | --- |
| [Now in Android SyncManager.kt](https://github.com/android/nowinandroid/blob/main/core/data/src/main/kotlin/com/google/samples/apps/nowinandroid/core/data/util/SyncManager.kt) | 阅读可观察状态和 requestSync 接口，采用共享状态与显式请求入口 | 使用 StateFlow 和 UseCase，不引入新框架 |
| [Now in Android SyncWorker.kt](https://github.com/android/nowinandroid/blob/main/sync/work/src/main/kotlin/com/google/samples/apps/nowinandroid/sync/workers/SyncWorker.kt) | 阅读 IO 执行、仓库结果汇合和启动任务 | 使用应用级协程、互斥与同一 Deferred 去重；不复制后台重试、分析事件或版本逻辑 |

## 实际验证

新增 9 个策略 JUnit 和 2 个首页回归：首次还原顺序、20 个并发调用只取一次、5 分钟/2 秒精确边界、失败后重试、等待者取消、应用级取消、固定失败状态、无缓存启动、列表/筛选保留及页面重建接续状态。与原测试合计 85/85，通过且无失败、错误或跳过。

离线 `testDebugUnitTest/assembleDebug/lintDebug` 成功（4 分 46 秒），Lint 0 错误、22 条既有警告。首轮构建脚本受 Gradle `java` 扩展名称遮蔽影响，改为显式导入 `java.net.URI` 后通过。

真机参数 `-PtoolboxDebugApiBaseUrl=http://127.0.0.1:8081/api/v1` 配合 `adb reverse tcp:8081 tcp:8081`，仅接受规定本地主机/端口/路径；默认模拟器、release HTTPS 不变。补充普通 INTERNET 权限。保留数据安装到 V2453A（Android 16），真实服务更新、失败保留、手动恢复、查询保留和远程条目移除已验证。完整范围见 [本轮记录](WEB_TOOL_ACCEPTANCE_2026-10-05.md)。
