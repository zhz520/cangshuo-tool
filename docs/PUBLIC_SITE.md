# 官网与网页工具目录

## 页面结构

- `/`：产品官网。包含平台定位、网页工具入口、Android 客户端入口、核心设计原则、使用流程、隐私与账户入口。
- `/tools/`：网页工具目录。使用与管理后台一致的侧边导航、顶部面包屑、指标行、搜索、分类、排序和卡片布局。
- `/tools/<code>/`：具体网页工具页面，继续使用原有路径和业务逻辑。

官网与目录共用 `deploy/site/site.css`、`deploy/site/site.js` 和 `deploy/site/theme-boot.js`。页面不依赖外部 CDN、远程字体或图标库。

## 视觉规则

- 颜色、边框、文字层级和状态色通过 CSS Variables 管理，并提供浅色与深色两套变量。
- 默认跟随系统主题。用户切换主题后只在本机 `localStorage` 保存偏好，不上传。
- 桌面端使用细边框、克制的黑白强调色和稳定间距；仅在网页工具网格保留轻量图标容器。
- `/tools/` 在桌面端使用 232px 固定侧边栏，在 899px 以下切换为紧凑分类条，在移动端使用两列工具栏和单列卡片。
- 页面支持 `prefers-reduced-motion`、可见键盘焦点、跳转正文链接、状态播报和 44px 触控目标。

## 数据与交互

目录页面从以下匿名接口读取数据：

```text
GET /api/v1/tools?page=1&pageSize=100
GET /api/v1/tools/popular?limit=100
```

只有 `mode=WEB` 且 `status=ENABLED` 的工具会显示。目录接口失败、超时或返回无效数据时，页面保留五个内置入口：

- 短视频解析
- 蓝奏直链解析
- 二维码工作台
- PDF 工作台
- 微信运动步数

搜索覆盖名称、描述、分类和关键词；支持分类组合筛选、推荐/使用量/名称排序与手动重新同步。接口请求使用 `no-store`、`credentials=omit` 和 `redirect=error`，状态通过 `role=status` 播报。

## 验证

静态契约与页面行为检查：

```powershell
python scripts/check_site_pages.py
node --check deploy/site/site.js
node --check deploy/site/theme-boot.js
```

浏览器验收覆盖桌面与 390px 移动视口、浅色/深色主题、移动导航、搜索、分类筛选、排序、空状态以及 API `503` 时的内置入口回退。页面控制台无错误。

本次不修改服务端接口、数据库、Android 客户端或网页工具业务逻辑。
