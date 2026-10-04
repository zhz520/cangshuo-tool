# 沧烁工具箱

一个以搜索为入口、优先在本地运行工具，并通过可选云端能力扩展的工具平台。项目采用单仓库管理 Android 客户端、Spring Boot API、Vue 管理后台和部署配置。

## 仓库结构

| 路径 | 内容 |
| --- | --- |
| `android/` | Kotlin、Jetpack Compose Android 客户端 |
| `server/` | Java 21、Spring Boot REST API |
| `admin/` | Vue 3、TypeScript 管理后台 |
| `deploy/` | Docker Compose 与部署配置 |
| `docs/` | 项目文档索引 |
| `toolbox-vibe-spec/` | 产品规格、路线图和编码规则（当前规范来源） |
| `stitch_cangshuo_tool_android_ui_redesign/` | Android UI 参考页面、截图和设计 token |

## 当前进度

Phase 0 基础设施门禁已通过，开始 Phase 1 的工具平台骨架。Monorepo 和 API 接口契约已建立；Android Compose 启动壳和 Gradle Wrapper 已落地，采用 AGP 9.1.0、Gradle 9.3.1、API 36 的兼容基线，Debug APK 构建与 Android Lint 已通过。

Android 的 `ToolMetadata`、`ToolDefinition` 与 `ToolRegistry` 已建立，统一 13 个分类、目录字段、运行模式及客户端执行入口，并提供编码去重、稳定排序、分类/推荐查询及状态/设备支持结果。注册中心已按 Compose → ViewModel → UseCase → Repository 接入首页，提供分类、工具列表、推荐与四个底部入口；当前已注册 [本地计算器](docs/CALCULATOR.md)，[本地搜索](docs/LOCAL_SEARCH.md) 支持中英文关键词、分类筛选和结果打开，[本地收藏](docs/LOCAL_FAVORITES.md) 使用 Room 保存匿名书签并提供共享星标，最近使用按后续任务开发。界面提供简体中文与英语，默认跟随系统、以中文为最终回退，语言规则见 [多语言说明](docs/ANDROID_LOCALIZATION.md)。页面与验证范围见 [首页说明](docs/ANDROID_HOME.md)，字段及执行边界见 [工具模型与注册中心说明](docs/TOOL_MODEL.md)。

Android 后续 UI 以用户提供的 Stitch 页面为参考，已有 [UI 规范](docs/ANDROID_UI_SPEC.md) 确定统一颜色、字体、组件和加载状态。共享主题、页面加载卡片与收藏保存进度已接入并通过构建/Lint；完整页面布局迁移和设备视觉验收随对应任务完成。

Spring Boot API 已提供健康检查、统一响应与异常处理、traceId、本地 Swagger / OpenAPI，以及 `GET /api/v1/tools` 目录列表实现。目录复用已有 MySQL 表，支持匿名分页和分类筛选，仅返回启用且未删除的工具与分类；接口范围与验证记录见 [目录接口说明](docs/TOOL_CATALOG.md)。启动方法见 [服务端说明](server/README.md)。

Admin 启动壳已完成，提供后台布局、路由导航、健康概览和集中 API 客户端；TypeScript 检查、生产构建及本地健康接口联调已通过。启动方法见 [后台说明](admin/README.md)。管理员认证和业务管理功能按 Phase 5 开发。

Docker Compose 本地/正式配置、Dockerfile、官网基础入口、Nginx 域名路由与后台 `/admin/` 路径已落地，API 支持跨域白名单。Linux 镜像构建和本地编排启动已通过，MySQL、Redis、API、Nginx/后台及可选 MinIO 均健康；浏览器已确认后台连接正常。本地后台为 [localhost:8088/admin/](http://localhost:8088/admin/)，详见 [部署说明](deploy/README.md)。正式域名尚未部署。

MySQL/Flyway 已接入服务端，创建工具分类和目录两张基础表，初始化 13 个分类；V3 已登记计算器元数据，数据库连接参与健康检查。Docker 和 Windows 本地构建、启动及迁移校验均通过，结构及迁移规则见 [数据库说明](docs/DATABASE.md)。

Redis 客户端已接入，配置认证、连接/命令超时及整体健康检查；Docker 和 Windows 本地启动及认证连接已核对，API 直连和后台代理均正常。缓存 Key、TTL 及业务接入约定见 [Redis 说明](docs/REDIS.md)。

GitHub Actions 工作流已配置 Android、Server、Admin、部署配置四项检查及汇总门禁。项目已推送到 [GitHub 仓库](https://github.com/zhz520/cangshuo-tool) 的 `main`，2026-10-03 [首次托管运行](https://github.com/zhz520/cangshuo-tool/actions/runs/37128607920) 五项全部通过，Debug APK、Lint 报告、服务端 JAR 和后台构建产物保留 7 天。当前门禁覆盖构建与静态检查；完整范围与操作见 [CI 说明](docs/CI.md)。

## 规范入口

- [完整项目规格](toolbox-vibe-spec/PROJECT_SPEC.md)
- [开发路线图](toolbox-vibe-spec/ROADMAP.md)
- [AI 编码规则](toolbox-vibe-spec/AGENTS.md)
- [API 接口契约](docs/API.md)
- [工具目录列表接口](docs/TOOL_CATALOG.md)
- [工具模型、注册中心与客户端执行契约](docs/TOOL_MODEL.md)
- [Android 首页与本地目录接入](docs/ANDROID_HOME.md)
- [Android UI 规范与统一加载效果](docs/ANDROID_UI_SPEC.md)
- [本地计算器](docs/CALCULATOR.md)
- [本地搜索](docs/LOCAL_SEARCH.md)
- [本地收藏与 Room 数据库](docs/LOCAL_FAVORITES.md)
- [Android 多语言](docs/ANDROID_LOCALIZATION.md)
- [数据库结构与迁移](docs/DATABASE.md)
- [Redis 连接与缓存约定](docs/REDIS.md)
- [CI 工作流与构建门禁](docs/CI.md)

开始任何实现前，请先阅读根目录 [AGENTS.md](AGENTS.md)。
