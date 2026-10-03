# 沧烁工具箱开发约定

## 开始修改前

- 阅读 `toolbox-vibe-spec/PROJECT_SPEC.md`、`toolbox-vibe-spec/ROADMAP.md` 和 `toolbox-vibe-spec/AGENTS.md`。
- 先检查仓库现状和可复用代码，再说明本次最小计划及文件范围。
- 每次只实现一个路线图任务；避免无关重构和无计划的依赖升级。

## 架构边界

- Android 使用 Compose + ViewModel + UseCase + Repository；UI 不直接调用 Retrofit 或访问 Room。
- Server 使用 Controller → Service → Mapper/Repository 分层，并校验请求、统一响应和错误格式。
- Admin 使用 Vue 3 + TypeScript；API 调用集中管理。
- 新增工具需使用唯一 code 和统一元数据，并注册到 ToolRegistry。
- 本地工具默认离线运行；不得把 API 密钥或其他秘密放进 Android 客户端或 Git。

## 数据、接口与安全

- 数据库结构变更必须新增 Flyway migration，不修改已执行的 migration。
- API 契约变更要同步更新 DTO、接口文档及相关客户端模型。
- 不记录密码、Token、API Secret 或用户敏感原文。
- 按需申请 Android 权限；服务端密钥从环境或密钥管理配置读取。

## 工作流程

- 控制改动范围，完成一个小任务后更新 `toolbox-vibe-spec/ROADMAP.md`。
- 有适用的构建或质量门禁时，执行并准确报告结果；工具链不可用时说明限制。
- 不宣称未经验证的功能已经完成。
- 提交信息使用 `feat:`、`fix:`、`refactor:`、`test:` 或 `chore:` 前缀。
