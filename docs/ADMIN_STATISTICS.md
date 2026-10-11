# 后台运营统计

2026-10-11。概览从健康检查升级为真实业务统计；Vue 3 复用页头、请求状态、表格和主题令牌，无图表/CDN 依赖。后端新增 statistics 的 Controller → Service → Repository；V43 追加每日采集表和查询索引，V1–V42 不修改。

## 接口与口径

`GET /api/v1/admin/statistics?days=30`：管理员 Bearer，支持 7/30/90，默认 30。匿名 401、普通用户 403、非法窗口 400/10001；统一信封与 traceId，`Cache-Control: no-store`。所有时间戳 UTC ISO；业务日期 Asia/Shanghai，起止日期均包含。

响应 data：`days/from/to/timezone/generatedAt/collectionSince/summary/daily/ranking/categories/modes/traffic`。

- summary：users/enabledUsers/disabledUsers（未删除账号），activeMembers（启用且 PRO 永久或尚未过期）、expiredMembers（PRO 到期）；newUsers 为窗口注册量。
- tools/enabledTools/disabledTools/maintenanceTools 为未删除工具。分类分布含空分类，模式 LOCAL/SERVER/HYBRID/WEB 均来自数据库。
- feedbackTotal/pendingFeedback/processingFeedback/resolvedFeedback；sites/pendingSites/approvedSites/rejectedSites 为当前状态。
- storedFiles/storedBytes 为尚未逻辑删除的对象，不表示磁盘实际容量或备份；syncedFavorites/syncedRecents 排除同步墓碑。
- allTimeOpens 为原累计打开量；windowOpens/todayOpens 为新每日表之和。每次既有工具打开 API，在同一事务中更新两张表；它不是处理成功次数或独立访客数，匿名上报仍受既有频率限制，不能用于支付结算。
- activeUsers 为窗口去重账号，dailyActiveUsers 为今日去重，weeklyActiveUsers 为近 7 日去重。仅成功的、已鉴权的用户 API 请求，排除管理员、匿名、4xx/5xx、OPTIONS 和删除账号请求。未登录的离线使用不属于此指标。
- aiAttempts 沿用现有 AI 表的 **UTC 配额日**，用与筛选相同的日期标签汇总，含失败尝试。未记录 token、费用和支付，故不展示此类指标。
- daily：date/registrations/opens/activeUsers/feedback/submissions，按日补齐，固定 7/30/90 行。注册、反馈、投稿可从已有记录重建；工具和活跃采集之前的日期在 UI/CSV 显示“未采集”，不会将旧累计数伪造成历史曲线；首个采集日可能不满 24 小时。
- ranking：前 20 个未删除工具，期间量 → 累计量 → code 排序；含停用/维护工具。code/name/category/status/opens/allTimeOpens。不保证展示全部工具，界面标明范围。
- categories/modes：code/name/count/opens，其中 opens 为该组未删除工具的累计量。
- traffic：since/requests/clientErrors/serverErrors/averageMs/activityWriteFailures，**本实例启动以来**的已结束 API 请求，重启清零、无跨实例合并；排除健康检查/OPTIONS。4xx/5xx 分开，平均时间包含鉴权。写活跃记录失败不影响业务响应，另计失败次数并显示提示。

工具每日表只保存日期、工具编码和计数，不存匿名用户或设备标识；活跃表只存账号 ID 和日期，不存请求正文/IP/URL，删除账号通过外键级联删除。删除账号后历史用户曲线会相应减少；不把删除的数据永久复制到分析表。请求指标仅内存聚合，不采集身份或正文。

## 交互

窗口及五种趋势切换、柱条点击/键盘选择日期、每日明细分页、日统计 CSV 导出、工具排行列排序、分类跳转筛选、待办跳转均连接真实数据。并发刷新采用现有请求版本隔离，失败保留上次结果且标明原统计范围。窄屏指标两列，表格和 90 天图表只在各自容器横向滚动；深浅色共享令牌，减少动画设置禁用柱条过渡。

## 验证

- Server：全量 153 项单测通过（新增 9 项，覆盖上海跨年/闰日/窗口校验、鉴权、错误计数与异常隔离）。
- Admin：TypeScript 与生产构建通过，解码器 21/21、并发请求 4/4 回归通过。
- 隔离 MySQL/API：`check_admin_statistics_runtime.py` 69/69 通过，覆盖全新 V1–V43 迁移、空数据、7/30/90 日期、401/403/400、12 次并发计数、趋势/排行一致、每日活跃去重、永久/过期/停用会员、删除级联；临时容器、数据库和数据库用户均清理。
- 生产：Server/Admin 已重建发布，四容器健康；正式 HTTPS API 的 7/30/90 日统计（各 29 项 summary）、采集写入错误为 0、匿名限制/非法日期校验、健康/热门排行/原工具目录回归通过。启动验收前的日统计不会补造历史。
- App 与官网统计说明已更新；中英 strings XML 解析通过，Android 完整构建记录跟随后续工具验收补记。
- 浏览器自动化初始化报“failed to write kernel assets / 系统找不到指定的路径”，重置后仍失败；不得把构建通过宣称为真实浏览器/手机验收。
