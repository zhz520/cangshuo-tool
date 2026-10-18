# 经期记录

## 定位

- 工具编码：`period_tracker`
- 分类 / 模式：`LIFE` / `LOCAL`
- 排序：636
- Android 数据：Room v14 `period_cycle`
- 服务端迁移：`V84__seed_period_tracker_tool.sql`
- 分层：Compose → `PeriodViewModel` → `PeriodUseCases` → `PeriodRepository` / `PeriodSettingsRepository`

本机记录每次经期的开始、结束、流量、症状、情绪和备注，在六周固定月历中显示实际与预测区间，并根据历史开始日计算平均周期、平均经期长度、下次预计日期和参考窗口。记录与设置不上传、不进入云同步，也不新增服务端业务表或健康数据 API。

## 开源参考

查阅日期：2026-10-18。以下项目只用于核验业务模型、预测边界和分类交互；没有复制源码、资源或依赖。

| 项目 | 固定版本与许可 | 实际核验源码 | 采用内容与适配差异 |
| --- | --- | --- | --- |
| [EmmaTellblom/Mensinator](https://github.com/EmmaTellblom/Mensinator) | [`f79504fbe61e43f4d6dc5b17e0f6040d342e18a5`](https://github.com/EmmaTellblom/Mensinator/commit/f79504fbe61e43f4d6dc5b17e0f6040d342e18a5)；MIT | [CalculationsHelper.kt](https://github.com/EmmaTellblom/Mensinator/blob/f79504fbe61e43f4d6dc5b17e0f6040d342e18a5/app/src/main/java/com/mensinator/app/business/CalculationsHelper.kt)、[PeriodPrediction.kt](https://github.com/EmmaTellblom/Mensinator/blob/f79504fbe61e43f4d6dc5b17e0f6040d342e18a5/app/src/main/java/com/mensinator/app/business/PeriodPrediction.kt)、[CalendarScreen.kt](https://github.com/EmmaTellblom/Mensinator/blob/f79504fbe61e43f4d6dc5b17e0f6040d342e18a5/app/src/main/java/com/mensinator/app/calendar/CalendarScreen.kt)、[CalendarViewModel.kt](https://github.com/EmmaTellblom/Mensinator/blob/f79504fbe61e43f4d6dc5b17e0f6040d342e18a5/app/src/main/java/com/mensinator/app/calendar/CalendarViewModel.kt)、[AndroidManifest.xml](https://github.com/EmmaTellblom/Mensinator/blob/f79504fbe61e43f4d6dc5b17e0f6040d342e18a5/app/src/main/AndroidManifest.xml) | 核验了 Compose 月历、选中/实际/预测状态的着色优先级、从经期开始日计算平均间隔、数据不足时不返回预测，以及无网络权限和禁止自动备份的本地隐私边界。本项目改为明确的周期实体和起止区间，不使用其逐日 `period_id` 平铺表，也不把流量塞进症状。当前版本仍未实现可选的截屏保护、通知与导入导出。 |
| [lekhanpro/cyclecare](https://github.com/lekhanpro/cyclecare) | [`8ea15d10dfe5dafa6cd3206774098ff1ca13a0e5`](https://github.com/lekhanpro/cyclecare/commit/8ea15d10dfe5dafa6cd3206774098ff1ca13a0e5)；MIT | [cycle_prediction_service.dart](https://github.com/lekhanpro/cyclecare/blob/8ea15d10dfe5dafa6cd3206774098ff1ca13a0e5/lib/features/tracking/domain/cycle_prediction_service.dart)、[cycle_models.dart](https://github.com/lekhanpro/cyclecare/blob/8ea15d10dfe5dafa6cd3206774098ff1ca13a0e5/lib/features/tracking/domain/cycle_models.dart)、[cycle_calendar.dart](https://github.com/lekhanpro/cyclecare/blob/8ea15d10dfe5dafa6cd3206774098ff1ca13a0e5/lib/widgets/cycle_calendar.dart)、[cycle_prediction_engine_test.dart](https://github.com/lekhanpro/cyclecare/blob/8ea15d10dfe5dafa6cd3206774098ff1ca13a0e5/test/unit/cycle_prediction_engine_test.dart) | 核验了开始日去重排序、异常间隔过滤、经期长度 1–14 天过滤、黄体期倒推、排卵日前 5 天到后 1 天的参考窗口、六周固定网格和预测边界测试。本项目采用最近 12 个有效周期平均值和固定 14 天黄体期，把估算明确标成日常参考而非避孕或医疗依据；没有采用其 JSON/Preferences 存储、联网字体、Firebase/Supabase 或 XOR 加密。 |

## 已实现功能

- 周期记录：开始日期、结束日期或“仍在进行”、少量/中等/较多流量、八种症状、六种情绪、最多 500 字备注。
- 日历：固定六周周一开头网格，实际经期、预计经期、参考窗口、症状/备注和今天使用不同且不只依赖颜色的标记；点击日期查看详情并新增或编辑该日所属记录。
- 预测：有效周期间隔限制为 15–60 天，取最近 12 个有效样本平均；平均经期长度取最近 12 个已结束记录的 1–14 天样本；历史不足时使用本机预设。
- 概览：当前周期天数、平均周期、平均经期、预计下次开始与结束、预测逾期天数、排卵日和参考窗口。
- 历史：按开始日期倒序显示全部周期，展示区间、持续天数、流量、进行中状态和症状，点击进入编辑。
- 设置：典型周期 15–60 天、典型经期 1–14 天；只在样本不足时作为默认值。
- 删除：编辑已有记录时必须再次确认；删除后日历和统计由 Room Flow 自动刷新。
- 中英文、深色主题、系统大字体、窄屏、软键盘安全区域、日期选择器、日历日语义和 Snackbar 反馈。

## 输入、预测与边界

- 日期范围 `1900-01-01..2100-12-31`，开始与结束日期不得晚于设备今天；单次记录最多 31 天。
- 记录区间不得与另一条记录重叠，开始日期唯一；不能同时存在两个“进行中”区间。
- 最多 1200 条周期记录；DAO 在事务中检查上限。新增/更新保持 `created_at`，只刷新 `updated_at`。
- 备注最多 500 个 Unicode 码点；输入框保留用户原文，超限时明确报错，不静默截断。
- 下次经期 = 最近开始日 + 平均周期。预测范围最多一个周期；排卵日 = 下次经期开始日 − 14 天，参考窗口 = 排卵日前 5 天至后 1 天。
- 预测日期已经过去时显示逾期天数，同时保留原估算，不自动伪造新的实际周期。
- 平均值使用四舍五入的整数天；非 15–60 天间隔和 1–14 天经期样本不参与平均。
- 当前数据模型把症状和情绪作为一次经期记录的汇总，不提供逐日症状、基础体温、宫颈表现、用药、流量逐日变化、通知提醒、桌面小组件或账号同步。
- 所有记录和设置仅在本机 Room/SharedPreferences。应用现有清单禁止自动备份；没有新增网络、通知、日历、传感器或存储权限。

## 并发、错误与恢复

- `PeriodViewModel` 合并 Room 周期 Flow 与 SharedPreferences 设置 Flow；数据库变化统一重算日历、统计和历史，页面不直接访问 DAO。
- 保存和删除期间阻止重复操作；失败保留编辑草稿并显示具体字段错误或本机存储错误。
- 月历与选中日期写入 `SavedStateHandle`，进程重建后恢复月份和日期；页面在生命周期前台每分钟刷新跨日状态。
- 日期选择器把可选结束日期限制到今天；未来日期可以浏览但编辑器会拒绝保存。
- Room `MIGRATION_13_14` 只新增 `period_cycle`、唯一开始日索引和更新时间索引，不修改任何已执行迁移或旧表。

## 验证范围

定向 JUnit **7/7** 覆盖周期平均、下一次预测、当前周期天数、逾期、黄体期与参考窗口、闰年六周网格、开始/结束和重叠校验、备注 Unicode 上限、设置回退、ViewModel 保存/月份 SavedState/设置委托、长备注保留和删除委托。

Android Debug/Release 各 **803/803**（121 个测试套件），Debug/Release Lint 均 **0 错误 / 91 条既有警告** 且经期记录源码、资源与测试无 finding；Debug APK **25,879,200** 字节、Release APK **17,445,441** 字节、AAB **16,847,638** 字节，Release artifact 门禁通过。服务端在 JDK 21 下 **226/226**、V84 静态契约 **129/129**，后台类型检查与生产构建通过。

Android 16 模拟器用真实 v13 数据库升级到 v14，确认 `recent_tool` 历史仍在、`period_cycle` 9 列和唯一开始日/更新时间索引与 schema 14 一致。随后验证新增经期（中等流量、腹痛、愉快、备注）、当前周期第 1 天、下次预计日期、参考窗口、历史记录、编辑、二次确认删除和 Snackbar。深色主题、2 倍字体和 720×1600 窄屏覆盖了全宽编辑/设置弹窗、日期与选项换行、固定底部操作和横向 48dp 日历；真机 TalkBack 和大量历史滚动仍待后续设备验收。

## 后续范围

- 独立每日流量/疼痛/体温/睡眠记录、多个未来周期、置信度与周期波动分析。
- 可选本地通知、导入导出或标准 AES-GCM 加密备份；这些需要单独处理密钥、通知权限和隐私确认。
- 真机 TalkBack 细读、低端设备大量历史滚动和更多 Android 尺寸矩阵。
- 预测仅作产品参考，没有临床验证，也不应被描述为避孕或诊断工具。
