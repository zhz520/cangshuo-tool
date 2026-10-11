# Admin 管理后台

Vue 3 + TypeScript + Vite + Element Plus + Pinia + Vue Router + Axios。依赖版本与锁文件保持项目现状。

## 当前能力

- 默认浅色开发者控制台：白色内容区、灰色导航、细分隔线和深色主要按钮；支持持久化深浅色切换、折叠侧栏、分组导航和手机导航抽屉。
- 管理员登录、路由守卫、账号菜单与退出；API 集中在 `src/api/`，处理统一响应、数据校验、错误及请求超时。
- 概览显示真实健康接口的连接、耗时与最近检查结果，不使用虚构业务统计。
- 工具、分类、推荐位、用户、公告、反馈与操作日志提供现有业务管理界面；系统配置标记为待开放。
- 列表统一筛选/分页（按接口能力）、加载/错误/空状态；六个常规业务列表与反馈在手机上使用有细分隔线的记录列表。
- 编辑/处理/详情采用右侧抽屉，窄屏占满可用宽度。保存期间防止关闭和重复提交，表单错误可见且自动获得焦点。
- 用户详情只显示脱敏会话、同步键与元数据；用户停用、分类停用、发布和删除仍沿用现有确认与限制。

当前视觉规范和验收范围见 [控制台设计系统](../docs/ADMIN_CONSOLE_DESIGN_SYSTEM.md)。历史迁移见 [UI 实施方案](../docs/ADMIN_UI_REWRITE_PLAN.md)，功能契约见 [API](../docs/API.md) 及 `docs/ADMIN_*.md`。

## 启动

需要 Node.js `^20.19.0 || >=22.12.0`。在 `admin/` 执行：

```powershell
npm.cmd ci
npm.cmd run dev
```

访问 [本机后台](http://127.0.0.1:5173)。开发服务只监听本机且严格使用 5173 端口。正常登录需要按 [服务端说明](../server/README.md) 启动服务并配置管理员账号；本次界面验收用的隔离示例接口不随正式应用提供。

配置可通过 `.env.example` / `.env.local` 调整：

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `/api/v1` | 浏览器 API 地址，构建时写入 |
| `VITE_APP_BASE_PATH` | `/` | 页面和静态资源路径，部署默认 `/admin/` |
| `API_PROXY_TARGET` | `http://127.0.0.1:8080` | Vite 开发代理目标 |

前端环境变量只能包含公开配置，不放入密钥或凭据。

## 构建与验证

```powershell
npm.cmd run typecheck
$env:VITE_APP_BASE_PATH = '/admin/'
npm.cmd run build
npm.cmd run preview
```

`build` 包含类型检查，产物在本项目 `admin/dist/`。预览监听 `127.0.0.1:4173`，不提供开发 API 代理，需使用可访问的 `VITE_API_BASE_URL` 重新构建。正式部署和 Nginx 深层路由回退见 [部署说明](../deploy/README.md)，实际发布范围见 [2026-10-11 部署记录](../docs/ADMIN_UI_DEPLOY_2026-10-11.md)。

在仓库根目录执行状态回归：

```powershell
node scripts/check_admin_ui_preferences.mjs
node scripts/check_admin_feedback_workspace.mjs
node scripts/check_admin_latest_request.mjs
```

2026-10-11 浅色控制台：生产构建、偏好回归、反馈 14/14、列表请求 4/4 通过。隔离示例接口检查了普通登录、工具编辑保存、反馈状态及回复保存、分页、空结果、503 提示和恢复、删除确认取消、侧栏折叠与主题切换；检查 1280/1024/768/390/320px 布局。各业务桌面列表加载成功，当前浏览器控制台警告/错误为空。截图与验证限制见控制台设计系统；历史 2026-10-07 验收保留在 UI 方案第 13 节。

## 结构

```text
src/
├── api/          集中请求、契约与响应校验
├── components/   导航、页头、筛选、状态、数据面板、卡片与抽屉
├── composables/  反馈工作区及最新请求归属
├── layouts/      后台框架
├── router/       业务路由、可用性元数据与认证守卫
├── stores/       认证、健康检查与界面偏好
├── styles/       语义主题、Element Plus、框架与页面样式
└── views/        各业务页面、登录与空状态
```

GitHub Actions 构建及产物流程见 [CI 说明](../docs/CI.md)。
