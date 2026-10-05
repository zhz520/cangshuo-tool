# 扫码历史

2026-10-05 已接入原生二维码工具 `qr` 的“扫码历史”标签。默认关闭，用户开启后记录相册识别、单码相机识别及点击完成的相机多码结果；生成二维码和查看旧历史不会再次记录。读取、写入、删除、清空都经过 ViewModel → QrHistoryUseCases → Repository → Room。

## 来源与采用

实际查阅日期：2026-10-05。

阅读 [ZXing 3.5.3 HistoryManager.java](https://github.com/zxing/zxing/blob/zxing-3.5.3/android/src/com/google/zxing/client/android/history/HistoryManager.java) 的 `addHistoryItem`、`deletePrevious`、`trimHistory`、历史查询/删除/清空逻辑（Apache-2.0）。采用“历史开关、重复内容更新、最新优先、有数量上限、敏感配置不记录”的思路。

本项目默认关闭而非该示例默认开启，最大 100 条而非 2000 条，按符号类型+原文 SHA-256 去重；Room 事务批量更新和裁剪，全部 I/O 在后台。不开启示例的外部 CSV 文件、外部目录写入或原始异常日志，不复制源码或新增依赖。GitHub 网页工具读取失败，随后 PowerShell HTTPS 成功读取该固定 tag 的业务源码；不把失败读取记为成功。

## 功能与边界

| 场景 | 行为 |
| --- | --- |
| 初次使用 | 开关关闭，不写扫码内容；在历史页说明存储范围，用户显式开启 |
| 查看结果 | 复用现有结果页的完整复制、分享及重新生成，不自动打开链接或执行载荷 |
| 重复 | 相同符号+原文只保留最新时间；不同符号同原文为不同记录，空格/换行/Unicode 不改写 |
| 上限 | 最新 100 条；单项最多 16,000 UTF-8 字节，一次最多 50 项；SQL 在 CursorWindow 前限制字节、投影及数量，约 1.6 MB 载荷上限 |
| 排除 | Wi-Fi/OTP 配置、空文本、非法代理项、超限内容不保存；其它扫描内容仍可能含私人数据，开启提示不声称通用敏感信息识别 |
| 开关关闭 | 停止新增，保留已有记录；可逐条删除或确认清空，清空不改变开关 |
| 并发 | 应用共享 Repository 用互斥串行写入；DAO 事务内再次检查开关，更新和保留 100 条为一次事务 |
| 读取防护 | 不显示未知符号、无效时间、损坏 key/payload、排除类型或超限记录 |
| 失败 | 固定双语反馈、可重试，不显示原异常/cause；历史失败不影响当前识别结果；取消向上传播 |
| 隐私 | 只保存于应用私有 Room，不记录日志，不上传；并非加密数据库或云端同步，不保留原图片/相机帧 |

数据库升为 Room v4，新增 `qr_scan_history(entry_key,payload,symbology,scanned_at)` 与 `qr_history_preference(singleton,enabled)`。`MIGRATION_3_4` 只建表，1→2→3→4 路径保留收藏、最近使用和目录缓存；无破坏性重建，无服务端/Flyway/API 修改，schema 4.json 纳入版本控制。

## 验证

- 新增 7 个持久化 JUnit：默认关闭、关闭保留、重复/格式区分、100 条裁剪/删除/清空、Wi-Fi/OTP/Unicode/体积/批次边界、原文保留、损坏和固定错误、取消。与既有测试合计 103/103 通过，0 失败/错误。
- `scripts/check_qr_history.py` 执行实际 3→4 SQL，比对 KSP 4.json 并检查三张旧表数据保留、默认关闭、查询数量/UTF-8 上限及清空保留开关，12 项通过。主机内存 SQLite不代替真实 Android migration。
- 离线测试/Debug 构建/Lint 一次成功，用时 2 分 39 秒；Lint 0 错误/22 条既有警告，无新历史模块问题。
- 未等待手机验收，设备上的系统选择器、扫码耗时、旧版本迁移和语言/大字号仍留到发布前补验。没有新增设备权限、依赖或外部消息发送。
