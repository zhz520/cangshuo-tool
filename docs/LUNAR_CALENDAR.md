# 万年历

## 定位

- 工具编码：`lunar_calendar`
- 分类 / 模式：`LIFE` / `LOCAL`
- 排序：623
- 数据迁移：`V70__seed_lunar_calendar_tool.sql`
- 分层：Compose → `CalendarViewModel` → `LunarCalendarUseCases` → `LunarCalendarRepository`

工具提供公历月历、农历日期、干支、生肖、节气、节日、每日宜忌、月份切换和日期详情。所有历法计算在设备本地完成，没有网络、登录或额外权限。

## 开源参考

查阅日期：2026-10-17。

| 项目 | 固定版本与许可 | 实际核验源码 | 采用内容与适配差异 |
| --- | --- | --- | --- |
| [6tail/lunar-java](https://github.com/6tail/lunar-java) | `0194eb4574f33ab056fe7cac62a9d8bf24272478`；MIT | [Lunar.java](https://github.com/6tail/lunar-java/blob/0194eb4574f33ab056fe7cac62a9d8bf24272478/src/main/java/com/nlf/calendar/Lunar.java)、[LunarYear.java](https://github.com/6tail/lunar-java/blob/0194eb4574f33ab056fe7cac62a9d8bf24272478/src/main/java/com/nlf/calendar/LunarYear.java)、[LunarMonth.java](https://github.com/6tail/lunar-java/blob/0194eb4574f33ab056fe7cac62a9d8bf24272478/src/main/java/com/nlf/calendar/LunarMonth.java#L80-L91)、[LunarTest.java](https://github.com/6tail/lunar-java/blob/0194eb4574f33ab056fe7cac62a9d8bf24272478/src/test/java/test/LunarTest.java#L221-L230) | 使用 Maven 依赖 `cn.6tail:lunar:1.7.7` 的 `Solar` / `Lunar` 获取农历月日、闰月、干支、生肖、节气、节日和每日宜忌。普通月使用正数月份；非法日期由库抛出异常，不静默修正。许可证随 APK 打包。 |
| [kizitonwose/Calendar](https://github.com/kizitonwose/Calendar) | `c2711b3c7fdf027e9a6bad55be9a7d979a288699`；MIT | [CalendarMonth.kt](https://github.com/kizitonwose/Calendar/blob/c2711b3c7fdf027e9a6bad55be9a7d979a288699/core/src/main/java/com/kizitonwose/calendar/core/CalendarMonth.kt)、[DayPosition.kt](https://github.com/kizitonwose/Calendar/blob/c2711b3c7fdf027e9a6bad55be9a7d979a288699/core/src/main/java/com/kizitonwose/calendar/core/DayPosition.kt)、[WeekDay.kt](https://github.com/kizitonwose/Calendar/blob/c2711b3c7fdf027e9a6bad55be9a7d979a288699/core/src/main/java/com/kizitonwose/calendar/core/WeekDay.kt) | 采用“月份持有周列表、日期带 in-current/out-date 位置”的建模原则，以及以真实 `LocalDate` 作为选择键。本项目没有引入该库，月网格固定为 42 格、周一开头，避免不同月份行数变化造成页面跳动；同时明确保留 1900–2100 范围。 |

## 日期模型

- 月网格固定 6 行 × 7 列，从包含本月 1 日的那一周周一开始，使用 `LocalDate` 计算真实前后月份日期。
- 网格单元格显示公历日号、农历日或农历月初、节气名称，并用小圆点标记有节日。
- 点击任意格会选中该日期；点击相邻月份补位日期时，页面切换到该日期所属月份。
- 月份切换保留当前日号，目标月份不存在该日号时收敛到月末，例如 1 月 31 日切到 2 月是 2 月 28 日或 29 日。
- 支持 1900-01 至 2100-12；更早或更晚的日期不进入 UI，SavedState 中损坏或越界值回退到今天。
- 今日与选中日期都使用北京时间 `Asia/Shanghai` 的日历日，避免设备时区导致中国农历日期差一天。
- 页面在前台时等待到北京时间下一个日界并刷新“今天”标记；离开页面取消等待。

## 详情内容

- 公历日期与星期。
- 农历月、农历日和闰月标记；例如 2028-06-23 显示闰五月初一。
- 年/月/日干支与生肖。
- 节气与公历/农历节日去重列表。
- 每日“宜 / 忌”，最多各展示 12 项，避免长列表压垮页面；复制摘要同样使用已保留项目。
- 年份、月份、选中日期写入 `SavedStateHandle`，旋转和页面恢复保持一致。
- 复制/分享完整选中日期摘要；本地库失败时显示固定错误和重试。
- 42 个日期格均带本地化无障碍描述，包含公历日期、农历/节气和节日名称，并暴露选中状态。

## 当前不支持

- 官方调休、法定假期连续区间、工作日提醒和节假日闹钟。
- 开始日期/结束日期区间事件、个人日程、提醒和同步。
- 宜忌流派切换、神煞/纳音等扩展黄历字段、真太阳时和命理排盘。
- 跨 1900–2100 年之外的历史/未来推演和桌面组件。

## 验证范围

定向测试覆盖：

- 2026 年 10 月 42 格周一开头月历、跨月补位和 31 个本月日期。
- 重阳节、春节、清明节气标签和节日圆点数据。
- 农历九月初九、干支/生肖/宜忌、2028 闰五月。
- SavedState 日期恢复、相邻月点击、月份切换日号收敛、今天定位、北京时间午夜刷新、边界年和 Repository 失败。

Android 16 模拟器实际验证：

- 2026 年 10 月月历、今天/选中态、节日圆点、9 月与 11 月补位日期正常显示。
- 选中 2026-10-18 显示“农历九月初九”、丙午年/戊戌月/甲子日、生肖马和宜忌列表。
- 切到 2026 年 11 月保留 17 日，选择 11 日后详情更新为十月初三、己亥月、己丑日。

最终本地门禁：Android **634/634**、`assembleDebug` 成功、Lint **0 错误 / 80 既有警告**，新增源码和资源无 finding；APK 为 **23,271,204** 字节；服务端 **226/226**。

服务端 V70 已上线：提交 `6578844` 与验收脚本提交 `8ca2efc` 已推送 GitHub；隔离候选 **189/189**，只读 HTTPS **130/130**，Flyway **70/70** 且零失败，五个生产容器 healthy，官网与后台均返回 200，LIFE 公开目录返回 `lunar_calendar`。生产 API 镜像为 `sha256:c2bda17d6bd85087b354024d889f42a80d12ae8f3d8cca2bb0104354230a904e`，回滚标签 `before-calendar-v70`。

真机、TalkBack、大字号布局和第三方黄历口径仍未验收。
