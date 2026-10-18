# 物品资产

## 定位

- 工具编码：`inventory_assets`
- 分类 / 模式：`LIFE` / `LOCAL`
- 排序：633
- 数据迁移：`V81__seed_inventory_assets_tool.sql`
- Android 数据：Room v12 `inventory_item`
- 分层：Compose → `InventoryViewModel` → `InventoryUseCases` → `InventoryRepository`

在本机记录物品名称、分类、位置、数量、单位、单价、低库存阈值、购买日期和到期日期。列表提供库存价值、低库存、30 天内到期和已过期统计，并支持搜索、筛选、排序、快速增减和完整增删改。

## 开源参考

查阅日期：2026-10-18。

| 项目 | 固定版本与许可 | 实际核验源码 | 采用内容与适配差异 |
| --- | --- | --- | --- |
| [google-developer-training/android-basics-kotlin-inventory-app](https://github.com/google-developer-training/android-basics-kotlin-inventory-app) | [`ce377892b66380a006ec4b4d4fa2f4baa14c16d0`](https://github.com/google-developer-training/android-basics-kotlin-inventory-app/commit/ce377892b66380a006ec4b4d4fa2f4baa14c16d0)；Apache-2.0 | [Item.kt](https://github.com/google-developer-training/android-basics-kotlin-inventory-app/blob/ce377892b66380a006ec4b4d4fa2f4baa14c16d0/app/src/main/java/com/example/inventory/data/Item.kt)、[ItemDao.kt](https://github.com/google-developer-training/android-basics-kotlin-inventory-app/blob/ce377892b66380a006ec4b4d4fa2f4baa14c16d0/app/src/main/java/com/example/inventory/data/ItemDao.kt)、[InventoryViewModel.kt](https://github.com/google-developer-training/android-basics-kotlin-inventory-app/blob/ce377892b66380a006ec4b4d4fa2f4baa14c16d0/app/src/main/java/com/example/inventory/InventoryViewModel.kt)、[AddItemFragment.kt](https://github.com/google-developer-training/android-basics-kotlin-inventory-app/blob/ce377892b66380a006ec4b4d4fa2f4baa14c16d0/app/src/main/java/com/example/inventory/AddItemFragment.kt)、[ItemDetailFragment.kt](https://github.com/google-developer-training/android-basics-kotlin-inventory-app/blob/ce377892b66380a006ec4b4d4fa2f4baa14c16d0/app/src/main/java/com/example/inventory/ItemDetailFragment.kt)、[ItemListAdapter.kt](https://github.com/google-developer-training/android-basics-kotlin-inventory-app/blob/ce377892b66380a006ec4b4d4fa2f4baa14c16d0/app/src/main/java/com/example/inventory/ItemListAdapter.kt) | 核验了实体字段、Room DAO、ViewModel 的加载/保存/删除、添加表单和详情/列表的数据组织。本项目保留“本地物品台账、单条详情、编辑与删除”的业务概念，但使用 Compose、Coroutines Flow、强校验 UseCase 和 Repository；增加分类、位置、单位、价格、低库存阈值、购买/到期日期、金额价值、统计、搜索、四种筛选、五种排序和原子快速增减。没有复制源码、资源或测试。 |

## 已实现功能

- 物品台账：名称、备注、七类分类、存放位置、数量、自定义单位、人民币单价、低库存阈值、购买日期和到期日期。
- 概览：物品数、总数量、已知价值、低库存数、30 天内到期数、已过期数。未填写价格的物品不计入已知价值。
- 搜索：名称、备注和位置；筛选：全部、低库存、30 天内到期、已过期；排序：最近更新、名称、数量、价值、到期日期。
- 卡片快捷 `+1/-1`，DAO 在同一条 SQL 中把数量限制到 `0..999999` 并更新修改时间；同一条目的并发点击按忙碌状态去重。
- 详情、编辑和删除；编辑覆盖全部字段，删除前显示不可恢复确认。
- 新建/编辑对话框在键盘、刘海和系统手势区域下使用安全边距；内容滚动，底部操作在横屏或较矮窗口中进入滚动区域。
- 中英文资源、浅色/深色主题、2 倍字体和窄屏换行适配；页面及列表滚动位置由 Compose 保存。
- 本地加载、空数据、搜索无结果、保存中、校验错误、存储失败和操作结果 Snackbar 状态。

## 输入、数据与边界

- 名称必填，最多 80 个 Unicode 码点；备注最多 500；位置最多 60；单位最多 12。连续空白会合并，首尾空白会去除。
- 数量与低库存阈值必须为 `0..999999` 的整数；低库存状态定义为 `数量 <= 阈值`。默认阈值为 0，因此数量为 0 时立即标记低库存。
- 单价可选，使用人民币元并精确到分；最大单价 `999999.99`，不接受负数、千分位、货币符号或超过两位的小数。
- 日期范围固定为 `1900-01-01..2100-12-31`；到期日不得早于购买日。日期选择器同时限制可选范围；修改购买日后如果超过原到期日，会清除到期日，避免保存一张无效表单。
- 到期状态以设备本地日期计算：早于今天为已过期，今天到未来 30 天（含第 30 天）为即将到期，其余为正常。应用保持前台时每分钟刷新，跨过午夜后自动更新。
- 最多保存 1000 条。Room `@Transaction` 在同一事务中检查计数并插入，避免多个界面实例或陈旧快照并发突破上限；数量修改使用单条夹取 SQL。
- 保存或读取 `SavedStateHandle` 中的搜索、筛选和排序，配置变更或页面返回后保持列表状态。
- 全部信息只保存在本机 Room 数据库，不上传、不同步；卸载应用或清除数据会永久删除记录。无 Android 权限、后台通知、系统提醒、云备份或跨设备能力。

## 验证范围

定向 JUnit 10 项覆盖 Unicode/备注/位置/单位预算、数量和阈值边界、单价解析与格式、日期顺序与极端日期、低库存与 30 天到期边界、搜索/筛选/排序/统计、SavedState、创建与编辑规范化、存储失败保留草稿、条目上限、缺失编辑、快速增减去重、删除、跨日刷新和失败消息。

Android 16 模拟器实测新增物品、编辑价格/低库存阈值、购买与到期日期、分类、快捷 `+1` 数值重算、低库存筛选、详情、删除确认、深色主题与 2 倍字体；2 倍字体同时验证软键盘弹出时输入框仍可见、卡片操作自动换行、编辑对话框字段可滚动且操作可达。只读审查后补充条目名称到 TalkBack 操作描述与删除确认，使用消息修订号避免连续同类型 Snackbar 被 StateFlow 去重，并修正宽屏对话框的 560dp 约束顺序。真实 Room v11 数据库覆盖安装 v12 后 `user_version=12`，`calculator` 最近使用记录与计数 1 保留，`inventory_item` 13 列和三个索引建立成功。

本地门禁：Android Debug/Release 各 **778/778**，115 个测试套件；`assembleDebug`、`assembleRelease` 与 `bundleRelease` 成功，Debug APK **24,748,008** 字节、Release APK **17,160,921** 字节、AAB **16,597,035** 字节；Debug/Release Lint 均 **0 错误 / 91 条既有警告**，物品资产源码、资源、测试与 schema 无 finding；Release artifact 门禁通过。服务端在 JDK 21 下 **226/226**、V81 离线静态契约 **132/132**，后台 `vue-tsc --noEmit` 与生产构建通过。

2026-10-18 发布验收：严格隔离候选 **303/303** 通过，使用一次性 Docker 网络、临时数据库/用户和临时 Redis，只从当前生产容器继承非秘密运行配置；候选启动、V81、后台编辑保持、排行榜与 7/30/90 天统计均通过，结束后资源全部清理。生产只读 HTTPS 验收 **203/203**；生产镜像 `sha256:5be4301f4029ce3a0f272b1e4b696b7e4a71f6024e083a8ab30ec4c98360d47f`，Git/Docker 回滚标签 `before-inventory-assets-v81`。Flyway **81/81** 且失败 **0**，最新记录为 `81 seed inventory assets tool`；五个容器 healthy，官网、后台登录页和 API 健康接口均返回 200。
