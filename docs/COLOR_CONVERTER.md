# 颜色转换

编码 `color_converter`，`CALC / LOCAL`，排序 55。Compose → ColorViewModel → ConvertColorUseCase → ColorRepository / LocalColorRepository；解析与转换全部本地完成，无网络、账号或权限要求。ToolRegistry 与 V46 工具目录使用相同名称、描述、图标与关键词。

## 开源参考与适配

实际源码查阅日期：2026-10-12。参考仓库 [Qix-/color-convert](https://github.com/Qix-/color-convert)（MIT，main 分支可变化；本次记录的提交 `5c106a633b5cd2de554d9c287ad31f9eeca7a271`）：

| 来源 | 实际采用内容 | 适配差异 |
| --- | --- | --- |
| [conversions.js](https://github.com/Qix-/color-convert/blob/5c106a633b5cd2de554d9c287ad31f9eeca7a271/conversions.js)，blob `c6aac2cc29c53dc7395a5b25e482d8bd71885b50` | 阅读 `rgb.hsl`、`rgb.hsv` 的公式与边界处理：delta 为零时饱和度归零、色相按 max 通道分支并支持负值回绕、HSV 使用 1/3、2/3 分数制 | 用 Kotlin 独立实现同一数学；HSL/HSV 反算采用经典 hue2rgb 与 HSV 扇区公式（color-convert 通过 route.js 走 `hsl.rgb`/`hsv.rgb`，本次未逐行读取那两个函数，公式为本项目独立编写的等价实现） |
| [LICENSE](https://github.com/Qix-/color-convert/blob/5c106a633b5cd2de554d9c287ad31f9eeca7a271/LICENSE)，blob `0f2106fcd58377b84a865e9c39b1b3bbf98d60ba` | MIT 许可确认 | 未复制代码、未引入 JS 依赖；仅保留来源记录 |

## 功能与边界

- 输入任意一种表示，实时输出四种格式：HEX（`#FF8800`）、`rgb(255, 136, 0)`、`hsl(32, 100%, 50%)`、`hsv(32, 100%, 100%)`，并显示色块预览与可复制的单行摘要。
- 接受格式：`#RGB` / `#RRGGBB`（可省略 `#`，不区分大小写）；`rgb(r,g,b)`（逗号或空格分隔，也可直接输入 `255,136,0`）；`hsl(h,s%,l%)` 与 `hsv(h,s%,v%)`（逗号或空格分隔，百分号建议但不强制）。
- 色相任意十进制数并按 360° 取模（`hsl(392,…)` 等于 `32°`）；RGB 通道限制 0–255，百分比限制 0–100，越界给出范围提示；8 位带 Alpha 的 HEX 提示「暂不支持透明度」。
- 显示精度：HEX 为精确值；色相显示为整数度；百分比保留 1 位小数（去尾零）。反算先四舍五入到整数通道。
- 输入上限 48 个字符：超长粘贴不覆盖旧值并提示；空输入显示引导文案。
- 暂不支持：透明度/Alpha、CSS 颜色名、HWB/CMYK/LAB/OKLCH 等扩展空间、调色板管理或历史记录；色块仅作展示，不含取色器（取色器为独立待建项）。

## 状态与界面

SavedStateHandle 保留输入文本；解析失败与解析成功分别显示错误卡或结果卡，空输入显示引导。结果卡复用 `ToolGroupCard`、`ToolResultRow`、`ToolboxTextButton` 与主题；色块随输入即时更新，窄屏与大字号下结果行自动上下排布。中英资源齐全，公式与边界均有单元测试。

## 实际验证

2026-10-12：`ColorConversionTest` 8 项算法测试覆盖 3/6 位 HEX（含省略 `#`）、8 位 Alpha 拒绝、`rgb()` 与裸三元组、越界与非数值输入、RGB→HSL/HSV 常见色、灰色零饱和度、`hsl`/`hsv` 反算、色相 360° 回绕与往返误差 ≤1 通道；`ColorViewModelTest` 2 项回归覆盖空输入引导、半成品输入错误、超长粘贴保留原值与清空恢复。本批 Android 全量 **300/300** 测试通过（54 个套件），`assembleDebug` 与 `lintDebug` 通过（0 错误、79 警告；新增文件无 lint 告警）。

未连接真机或模拟器：色块预览、复制行为、键盘与大字号仍待设备验收；未与浏览器开发者工具逐色对拍，往返测试以通道误差 ≤1 为界。
