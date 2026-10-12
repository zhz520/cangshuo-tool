# 油耗计算

编码 `fuel`，`CALC / LOCAL`，排序 45。Compose → FuelViewModel → CalculateFuelUseCase → FuelRepository / LocalFuelRepository。计算不请求网络、账号或权限；ToolRegistry / V44 同步目录，后台按既有管理与使用统计流程接入。

## 开源参考与适配

实际源码查阅日期：2026-10-12。

| 来源 | 实际采用内容 | 适配差异 |
| --- | --- | --- |
| [FuelTracker / src/lib/units.ts](https://github.com/Tellicious/FuelTracker/blob/main/src/lib/units.ts)，blob `f2935aae185cf7a3904d5abe2d7bf1a946f824f5` | 实际阅读 km/L 与 L/100km 的倒数换算及单位展示边界 | MIT，独立实现，增加英里、US/UK 加仑和两种 MPG；统一先归一化到 km/Litre，不复制其前端框架 |
| [FuelTracker / src/lib/derive.ts](https://github.com/Tellicious/FuelTracker/blob/main/src/lib/derive.ts)，blob `59aaec4f716890dc22a1da3aa0305374d6b711ac` | 阅读加油量×单价=费用和零值除数保护 | 使用可选元/升油价直接推算本区间费用；不加入上游多车账本、三字段自动回填和 IndexedDB 导入导出 |

两链接为可变化的 main 分支源码，仅上述函数属于已查阅范围。

## 功能和边界

- 输入本次加满油区间的行驶里程、加油量；公里/英里，升/US gallon/UK gallon 可选。单位切换重新解释字段，输入数字保持不变。
- 标准化后输出 L/100km、km/L、US MPG、UK MPG；1 mile = 1.609344 km，US gallon = 3.785411784 L，UK gallon = 4.54609 L。结果均来自同一里程/油量，不能混用两种加仑。
- 油价选填且固定为**元/升**，不随输入加仑选项改变；输出总油费、元/km、元/100km。留空不显示费用；0 表示免费，合法。
- 归一化里程 0.1–100000 km，油量 0.1–10000 L，油价 0–100 元/升。先检查正数、有限值和范围再除法；空/未填完整、非法/负数/NaN/Infinity 和范围错误明确区分。
- 每字段最多 24 字符，纯十进制；超长输入拒绝采用且保留前值。输入小数点不依赖区域设置；结果按设备区域格式显示，油耗两位、MPG 一位，费用 HALF_UP 两位。
- 支持清空和复制油耗/效率摘要；不含车辆档案、加油历史、里程表自动差值或长期平均。费用当前以结果查看为主，复制摘要不包含费用，不将未复制字段描述为已导出。

## 状态与界面

SavedStateHandle 保留输入与单位；未知单位回退公里/升。计算恒定预算，StateFlow 串行更新；快速编辑最终结果归属于最新字段，清空或有效编辑可恢复错误。页面复用共享主题、输入/按钮及 `ToolResultRow`，窄屏/大字号上下排布，单位选项换行，中英一致，无人工加载等待。

## 实际验证

2026-10-12：4 项算法测试覆盖多结果换算、两种英制与公制一致、可选油费及非法/超范围输入；真实 ViewModel 回归覆盖快速编辑、超长粘贴、移除油价及清空。本批 Android **280/280**，APK/Lint 通过（0 错误、72 警告），四工具目录对照通过。服务端发布验收见路线图。

本次没有连接真机/模拟器；复制、大字号、键盘、旋转与 TalkBack 待设备验收。未用实车加油记录验证测量误差。
