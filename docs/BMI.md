# BMI 计算

编码 `bmi`，`CALC / LOCAL`，排序 30。Compose → BmiViewModel → CalculateBmiUseCase → BmiRepository / LocalBmiRepository；无网络请求、账号要求或 Android 权限。ToolRegistry 与 V44 工具目录使用相同名称、描述、图标和关键词，后台复用既有工具管理。

## 开源参考与适配

实际源码查阅日期：2026-10-12。以下分支链接会变化，记录的是本次返回的文件 blob。

| 来源 | 实际采用内容 | 适配差异 |
| --- | --- | --- |
| [epmobile 的 BMI.kt](https://github.com/mannd/epmobile/blob/master/app/src/main/java/org/epstudios/epmobile/features/calculators/data/BMI.kt)，blob `caf99f65d82157435fc014e61ca7504a2fe0a6fb` | 阅读 `calculateCm`、单位输入与 WHO 分类分支，参考计算与展示分离 | GPL-3.0-or-later 源码仅供理解，未复制。独立实现；不采用近似因子 703，改用英寸/磅精确定义；分类使用未舍入值，显示单独舍入；额外支持中国成人标准 |
| [国家卫健委 WS/T 428-2013 发布页](https://www.nhc.gov.cn/wjw/yingyang/201308/a233d450fdbc47c5ad4f08b7e394d1e8.shtml)、[WHO 成人分类表](https://applications.emro.who.int/dsaf/EMROPUB_2014_EN_1822.pdf?ua=1)、[NIST SP 811 单位换算](https://www.nist.gov/pml/special-publication-811/nist-guide-si-appendix-b8) | 指定成人标准与单位定义 | 明确标注采用 WS/T 428-2013；不将 2025 年征求意见稿视为已发布新标准。卫健委正文读取返回 412，仅取得发布页的搜索索引；WHO 分类和已明确的 2013 标准作为实现依据 |

## 功能和输入边界

- 输入身高、体重，即时显示 BMI、分类、参考体重范围，可清空及复制摘要。
- 公制为 cm/kg，英制为 in/lb；1 in = 2.54 cm，1 lb = 0.45359237 kg。切换单位会重新解释现有输入，输入框单位同步变化。
- 中国标准：18.5、24、28 为分类分界；WHO 标准额外展示消瘦分级和肥胖 I/II/III 级。按未舍入 BMI 判定，显示保留 1 位小数；参考体重上限采用常规展示值 23.9 / 24.9。
- 仅用于成人体重参考；页面说明不适用于儿童、孕妇和运动员的特殊评估，不提供诊断或治疗建议。
- 归一化身高 50–260 cm，体重 10–500 kg；空表单显示引导，未填完整不显示结果，零、负值、非法格式、NaN/Infinity、超范围均失败。
- 单字段最多 12 个字符。纯十进制、点号小数和可选符号；不接受指数、分组逗号或区域小数逗号。超长粘贴拒绝采用，保留原输入并明确提示。
- 无持久化体重记录、导出文件、年龄/性别百分位或专业医学模型；复制的是实际计算摘要。

## 状态与界面

SavedStateHandle 保留表单与单位/标准；枚举损坏回退到默认选项。计算有严格常数预算，StateFlow 串行更新；清空和重新输入可恢复失败状态。共享输入框、按钮、分组组件与主题，中英资源齐全；窄屏选项换行，页面可随键盘滚动，不人工加入加载延迟。

## 实际验证

2026-10-12：BMI 算法 5 项测试，另有真实 ViewModel 的恢复、单位切换、超长粘贴和清空回归；本批 Android 全量 **280/280** 测试通过，`assembleDebug` 与 `lintDebug` 通过（0 错误、72 警告）。四工具目录名称/描述/code/关键词对照通过。具体发布和服务端验收见路线图本批记录。

本次没有连接真机或模拟器，不能把构建结果视为实机视觉验收；大字号、TalkBack、各厂商键盘和旋转恢复仍需设备验证。
