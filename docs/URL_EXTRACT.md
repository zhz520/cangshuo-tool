# 网址提取

编码 `url_extract`，`TEXT / LOCAL`，排序 70。Compose → UrlExtractViewModel → ExtractUrlsUseCase → UrlExtractRepository / LocalUrlExtractRepository；提取全部本地完成，无网络、账号或权限要求。ToolRegistry 与 V47 工具目录使用相同名称、描述、图标与关键词。

## 开源参考与适配

实际源码查阅日期：2026-10-12。

| 来源 | 实际采用内容 | 适配差异 |
| --- | --- | --- |
| [markdown-it/linkify-it](https://github.com/markdown-it/linkify-it)（MIT，提交 `14cb9b90ef7d94a1ef58df52fa59da10a1721cdb`）：[src/linkifyit.ts](https://github.com/markdown-it/linkify-it/blob/14cb9b90ef7d94a1ef58df52fa59da10a1721cdb/src/linkifyit.ts)（blob `8c2d098c1f3d9be5090f273d41fb114525ee414f`）与 [src/rebuilder.ts](https://github.com/markdown-it/linkify-it/blob/14cb9b90ef7d94a1ef58df52fa59da10a1721cdb/src/rebuilder.ts)（blob `55fbd6904e2e8b89423be41f2b485a2cae617317`） | 阅读其匹配语义与选项模型：`fuzzyLink` / `fuzzyEmail` / `tlds` / `maxLength`；分隔符按 Unicode 标点、空白与控制字符分类；成对括号用有界嵌套配对处理 | 独立实现，未复制代码。仅提供两种模式（完整链接 / 含 www 与裸域名），裸域名使用人工整理的常见后缀表；尾随标点与不配对收尾括号用独立扫描器裁剪；不支持邮箱、模糊 IP、`--` 长破折号与自定义 TLD 列表 |
| [cssnr/link-extractor](https://github.com/cssnr/link-extractor)（GPL-3.0，提交 `9ae583b4d2305e8361bfb55478c4ecfd367efb7b`），[src/js/extract.js](https://github.com/cssnr/link-extractor/blob/9ae583b4d2305e8361bfb55478c4ecfd367efb7b/src/js/extract.js)（blob `a86f871f278d6e4f8a598e27a908029e3fa2c8e6`） | 仅阅读其产品形态（DOM 链接收集、选区提取、复制工作流）以理解同类工具的用户预期 | GPL-3.0 源码不复制、不改编；本工具面向粘贴文本，采用正则扫描而非 DOM 采集 |

## 功能与边界

- 两种模式：仅完整链接（http / https / ftp，含 `www.` 前缀）或含 www 与裸域名（限定常见后缀表，如 com/cn/org/io/dev/top/xyz 等）。
- 自动裁剪尾随标点（中英文逗号、句号、引号、书名号等）与不配对的收尾括号；成对括号保留。
- 去重可选（忽略大小写、保留首次出现顺序）；邮箱地址中的域名、版本号（如 1.2.3）、缺少主机名的 `http://`、无点主机名不会误报。
- 结果上限 1000 条（超出标记截断）；界面最多展示前 50 条，复制包含全部结果。
- 复制格式：每行一个或 Markdown 链接；输入上限 20 万字符。
- 明确不支持：邮箱提取、IPv6 字面量之外的模糊 IP、`--` 长破折号规则、IDN 国际化域名还原、链接可用性检测（不联网）。

## 状态与界面

SavedStateHandle 保留文本、模式、去重与复制格式；结果卡显示数量、可选择的链接列表与提示（未找到 / 截断 / 仅展示前 N 条）；空输入显示引导，超长显示错误并保留旧值。复用共享 `ToolGroupCard`、`ToolboxTextField`、`ToolboxSwitch`、`FilterChip` 与主题。中英资源齐全。

## 实际验证

2026-10-12：`UrlExtractTest` 6 项覆盖完整链接与尾随标点裁剪、括号配对、模糊模式的邮箱 / 版本号 / 无主机名排除、忽略大小写去重与顺序、两种复制格式、1000 条上限与截断标记；`UrlExtractViewModelTest` 2 项覆盖空输入引导、模式与格式切换（含复制文本）以及超长粘贴保留原值。本批 Android 全量 **325/325** 测试通过（60 个套件），`assembleDebug` 与 `lintDebug` 通过（0 错误、83 警告；新增文件无 lint 告警）。

2026-10-12 模拟器（Android 16）对 see https://example.com/a?b=1 and http://foo.cn/x 提取出 2 条完整链接；复制与大字号仍待验收。正则未与 linkify-it 官方测试集逐条对拍，边界以本仓库测试为准。
