# Ping

2026-10-05，ping / NETWORK / LOCAL；Compose → PingViewModel → ExecutePingUseCase → NativePingRepository，V27 元数据。使用已有 INTERNET 权限，无新库或服务器代理；只在用户执行时向指定主机发诊断请求。

## 功能与边界

- 域名（含 IDN）、IPv4、IPv6 模式；发送 1/4/8 个 ICMP 包，显示实际发送/接收、丢包、最小/平均/最大 RTT、已解析回包及可获得的目标 IP；取消、复制/分享整份汇总。地址输入按 SavedState 恢复，结果不持久化或上传。
- 域名 STD3/ASCII label 与长度检查，不接受 URL、端口、空 label、控制字符、命令字符或选项。IPv4 四段 0…255、不接受多余前导 0；IPv6 仅校验包含冒号的字符限定字面量，支持包裹方括号，必须选择 IPv6 模式；域名由原生程序按族解析。作用域 `%wlan0`、IPv4 映射 IPv6 与尾点域名不在当前输入范围。
- 使用固定 `/system/bin/ping` 或 `ping6`，ProcessBuilder 独立参数数组，不拼 shell。最多一个进程，启动间隔 ≥2s，单包等待 2s、原生 deadline 15s、协程总期限 20s；取消/后台/离页（包含 Activity 转屏重建）强制结束进程并关闭流。原生程序可执行性/厂商限制不支持时明确反馈，不把 TCP 探测冒充 ICMP。
- 进程输出最多 64KiB，流可用时读取、无输出时可取消轮询，不先 waitFor 后堵塞输出；固定 LANG/LC_ALL=C，解析 Linux/iputils/toybox 常见英文 summary/rtt 与 seq/time。其他实现/格式显示解析失败，无原始 stderr、堆栈或目标日志。
- 统计用实际发送/接收范围校验；0 回复仍是 100% 丢包结果，不以字符串包含“0%”误判成功。RTT 必须非负有限、min≤avg≤max，仅采用完整原生统计，没有 summary 时显示未提供；`time<1` 回包保留 `<`，不当成精确 1ms。ICMP 无回复可能被过滤，不保证等于断网。取消和新执行通过 generation 隔离，旧 finally 不覆盖新状态。

## 实际源码参考

2026-10-05 通过 GitHub 官方 contents API 完整阅读 [AndroidNetworkTools PingNative.java](https://github.com/stealthcopter/AndroidNetworkTools/blob/main/library/src/main/java/com/stealthcopter/networktools/ping/PingNative.java)、[PingResult.java](https://github.com/stealthcopter/AndroidNetworkTools/blob/main/library/src/main/java/com/stealthcopter/networktools/ping/PingResult.java)、[PingStats.java](https://github.com/stealthcopter/AndroidNetworkTools/blob/main/library/src/main/java/com/stealthcopter/networktools/ping/PingStats.java)。main 为可变化分支，文件 blob c9163fd12aac6c0f69fa7a880289ecfe7c345c74 / 673e7a28cdb486641a8bc15845e6375d16f388c5 / e6d097872eb0548b68f2c0dbf22c28ed19f3ced6（非仓库 commit）；浏览工具无法取得内容后改用 API，没有下载文件。

采用原生 ICMP、IPv6 程序选择及 summary/RTT 数据思路；本项目不采用其 Runtime 拼字符串、先等待后读取或“0%”子串判定，补参数校验、部分/全部丢包、取消/并发/期限和输出上限，不把失败样本计入平均 RTT。原生分层独立实现，未复制第三方源码或引入该库。

## 验证

新增 IDN/IPv4/IPv6 与命令/URL拒绝、部分丢包、全部丢包、无效统计/DNS回归。新增 4 项 IDN/IPv4/IPv6 与命令/URL 拒绝、部分丢包、全部丢包、无效统计/DNS 回归；182/182 Android 测试通过，assembleDebug 与 lintDebug 成功（1 分 57 秒），Lint 0 错误/29 既有警告。Server 镜像成功（测试跳过），V27 success=1，8081/8088 total=19，ping/NETWORK/LOCAL。没有调用真机 ping、验证厂商命令选项或公网 ICMP，按用户要求留到发布验收。
