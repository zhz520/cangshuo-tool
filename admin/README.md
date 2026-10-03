# Admin 管理后台

管理后台使用 Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router 和 Axios。依赖固定版本，`package-lock.json` 纳入版本管理。

## 当前能力

- 后台布局、分组导航、窄屏导航抽屉、路由标题及页面不存在提示。
- 概览页调用真实的 `GET /api/v1/health`，显示连接状态、请求耗时、最近检查时间，并支持手动刷新。
- API 请求集中在 `src/api/`，校验统一响应与业务数据；处理业务错误、响应异常、连接失败和 8 秒超时。
- Pinia 管理健康检查状态；错误响应提供 traceId 时，页面显示可复制的请求编号。
- 工具、分类、用户、公告、推荐位、反馈、系统配置、操作日志及登录页面提供“即将开放”入口。

管理员认证及业务管理功能按 Phase 5 实现；当前没有登录会话、业务数据或可用的 CRUD 操作。

## 安装与启动

需要 Node.js `^20.19.0 || >=22.12.0` 及 npm。本机已使用 Node.js 22.13.0、npm 11.0.0 构建成功。

在本目录执行，Windows PowerShell：

```powershell
npm.cmd ci
npm.cmd run dev
```

macOS / Linux：

```sh
npm ci
npm run dev
```

访问 [localhost:5173](http://localhost:5173)，默认进入概览。开发服务只监听本机 `127.0.0.1:5173`；端口占用时会明确报错。前台运行时按 `Ctrl+C` 停止。

健康检查需要同时启动服务端，操作见 [服务端说明](../server/README.md)。服务未启动时，概览会显示连接异常并提供重新连接按钮。

### 环境配置

默认配置可以直接运行。需要修改时，将 `.env.example` 复制为 `.env.local` 并调整后重启 Vite：

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `/api/v1` | 浏览器中的 API 基础路径，构建时写入产物 |
| `VITE_APP_BASE_PATH` | `/` | 页面和静态资源基础路径；Docker 镜像使用 `/admin/` |
| `API_PROXY_TARGET` | `http://127.0.0.1:8080` | Vite 开发代理的服务端地址 |

开发请求经过 Vite 的 `/api/v1` 代理。前端环境变量只能包含公开配置，不放入密钥或凭据。

### 正式环境规划

官网位于 `https://tool.zhzgo.cn/`；管理后台默认部署到 `https://tool.zhzgo.cn/admin/`，请求 `https://toolapi.zhzgo.cn/api/v1`。

Dockerfile 和 Compose 已配置 Vite 的 `/admin/` 基础路径、生产 API 地址及 Nginx 页面路由回退；API 已支持精确的跨域白名单。Linux 镜像构建及本地容器运行已通过，访问 [localhost:8088/admin/](http://localhost:8088/admin/)；浏览器显示平台服务运行正常。直接运行 Vite 仍默认使用根路径。正式域名尚未部署。完整步骤见 [部署说明](../deploy/README.md)。

## 构建

```powershell
npm.cmd run typecheck
npm.cmd run build
npm.cmd run preview
```

`build` 包含 TypeScript 检查，产物位于 `dist/`。`preview` 仅用于查看构建产物，监听本机 `127.0.0.1:4173`，沿用配置中的 API 代理；它不是生产服务器。按环境设置 `VITE_APP_BASE_PATH` 和 `VITE_API_BASE_URL` 后构建；预览时使用同一组环境变量。正式部署使用独立 API 域名，Nginx 为 `/admin/` 页面路由配置 `/admin/index.html` 回退。

## 代码结构

```text
src/
├── api/          Axios 客户端、统一响应模型、健康接口与数据校验
├── components/   共用导航
├── layouts/      后台布局
├── router/       路由与导航元数据
├── stores/       健康检查状态
├── styles/       全局样式与响应式布局
└── views/        概览、待开放模块及页面不存在提示
```

页面通过状态管理调用集中 API 模块，不在组件中直接创建 Axios 客户端。当前接口契约见 [API.md](../docs/API.md)。

GitHub Actions 使用 Node `22.23.3`、`npm ci` 和 `npm run build` 检查正式环境产物，并保存 `dist/`。工作流及验证范围见 [CI 说明](../docs/CI.md)。

## 本机构建与运行记录

2026-10-03：

- `npm.cmd run build`：TypeScript 检查和 Vite 生产构建通过。
- 本地页面通过开发代理连接健康接口，显示“平台服务运行正常”。
- 浏览器中确认导航抽屉、工具管理入口与返回概览；浏览器记录未出现警告或错误。
- Docker 构建通过；Nginx 提供 `/admin/` 页面及深层路由，浏览器中的容器后台连接健康接口正常，控制台未记录警告或错误。
- 本次未建立或运行自动化测试；连接失败、超时及异常响应分支尚未逐项做运行验证。

本次后台预览的 PID 和日志位于 `node_modules/.cache/run/dev.pid`、`dev.stdout.log`、`dev.stderr.log`。需要关闭该预览时，在本目录执行：

```powershell
$adminProcessId = [int](Get-Content -LiteralPath 'node_modules/.cache/run/dev.pid')
$adminProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$adminProcessId"
$viteEntry = (Resolve-Path -LiteralPath 'node_modules/vite/bin/vite.js').Path
if ($adminProcess -and $adminProcess.CommandLine.Contains($viteEntry)) {
    Stop-Process -Id $adminProcessId
}
```
