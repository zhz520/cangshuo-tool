# AI 接手说明（新窗口 / 换模型）

本文件给新的 AI 会话使用：新开窗口或切换模型时，把下面「开场消息」整段发给 AI，让它从仓库读取现状，不依赖人复述。

## 开场消息（复制粘贴）

请接手「沧烁工具箱」项目开发。

- 项目目录：D:\codeStudy\code\cangshuo-tool（远端 https://github.com/zhz520/cangshuo-tool，主分支 main）
- 先按顺序阅读：
  1. toolbox-vibe-spec/AGENTS.md（开发约定）
  2. toolbox-vibe-spec/ROADMAP.md（进度与下一步）
  3. toolbox-vibe-spec/PROJECT_SPEC.md（完整设计）
  4. 涉及 Android UI 时再读 docs/ANDROID_UI_SPEC.md，视觉参考 stitch_cangshuo_tool_android_ui_redesign/
- 从 ROADMAP 中第一个未完成任务开始；每次只做一个任务，先说明最小计划和文件范围，再实施。
- 完成后执行适用的构建/Lint，更新 ROADMAP 与对应 docs，提交信息使用 feat:/fix:/refactor:/test:/chore: 前缀。
- 新建文件保存到 D 盘：项目文件放项目目录，独立交付物放 D:\CodexData\outputs，临时文件放 D:\CodexData\temp。
- 用中文交流。

当前进度（截至提交 2cc3b65）：
- Phase 1 已完成：首页、工具注册、Server GET /tools、计算器、本地搜索、本地收藏、本地最近使用；设备运行验证仍待做。
- 下一步：Phase 2「单位转换」，之后是时间戳、UUID、Base64、URL 编解码、Hash、JSON、文本工具、二维码、图片压缩。
- 生产域名规划：官网/后台 tool.zhzgo.cn，API toolapi.zhzgo.cn。
- 本地环境：Docker 数据在 D:\DockerData；Compose 项目 cangshuo-toolbox-local（server 8081、admin 8088、mysql 3307、redis 6380）。

## 使用说明

- Codex 桌面版：把会话工作目录设为 D:\codeStudy\code\cangshuo-tool 后，项目 AGENTS.md 会自动加载，但仍建议发一次开场消息说明当前任务。
- 同一会话内换模型：上下文自动延续，不需要特殊说明，换完后直接说「继续」即可。
- 新窗口最容易出问题的是 AI 凭记忆假设进度。第一句一定要让它先读 ROADMAP 再动手；仓库文档比任何口述摘要都新。
- 如果新会话没有本机文件访问能力，先让它从 GitHub 仓库读取上述文件。
