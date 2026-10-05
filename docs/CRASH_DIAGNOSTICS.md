# 基础崩溃诊断

2026-10-06，范围是本机可查看的诊断，不包含自动上传、远端聚合或告警服务。用户在「我的 → 崩溃诊断」查看、刷新、复制或确认清空。复制后由用户自行选择提交给支持；程序不会发送邮件。

Android 30+ 通过 ActivityManager.getHistoricalProcessExitReasons 读取本包最多 20 条系统记录，仅取 timestamp/reason，筛选 Java 崩溃、native 崩溃和 ANR。不读取 description、processStateSummary、traceInputStream、内存转储、用户输入、账号标识或设备型号。近 7 天、最多 10 条，同类型 2 秒内重复合并，按新到旧显示。系统可能丢弃/不提供历史；不把「无记录」解释为从未崩溃。

全部支持版本（26+）的 Application 安装一个简单 uncaught handler，原子保存最近一次 Java 崩溃时间，再把原始 Thread/Throwable 委托 Android 既有 handler；不读取异常类型、message 或 stackTrace。记录失败，包括低内存错误，仍委托既有 handler。跨线程争用时不等待锁；不是完整 Java/native/ANR 捕获保证。初始化前的崩溃只能依赖支持的系统历史。

私有 noBackupFilesDir 中仅保存最近 Java 时间和清空截止时间，128 字节读取上限，AtomicFile 写入。清空删除显示范围内的本地时间并隐藏历史截止前记录，不能删除 OS 自己维护的退出记录。报告只有应用版本、SDK、系统历史是否可用、UTC 时间和枚举类型，不自动上传、不请求新权限。

架构：Compose → DiagnosticsViewModel → DiagnosticsUseCases → LocalDiagnosticsRepository。复用共享主题和加载组件，不注册为独立工具，无新依赖/数据库表。服务器保留既有健康检查、脱敏错误/traceId 与容器滚动日志；正式外部集中告警若未来需要，须另行选择服务及更新政策。

源码/文档查阅日期：2026-10-06。
- [AOSP ApplicationExitInfo.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/app/ApplicationExitInfo.java)：采用公开 reason 常量和 timestamp，避免 description/trace 可能包含原文。
- [Android ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo)：API 30+ 和系统历史边界。
- [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile)：完成/失败写入协议，应用自己同步访问。

测试覆盖时间窗/未来时间/清空截止、正常退出过滤、同类型去重/数量、不同故障类型，以及记录正常/失败时始终委托原异常。真实设备的系统历史、故障/OOM/ANR 捕获和剪贴板按用户要求延期，不在设备上主动制造崩溃。

实际验证：Android 213/213 JUnit 通过，assembleDebug/lintDebug 通过（1 分 58 秒）。
