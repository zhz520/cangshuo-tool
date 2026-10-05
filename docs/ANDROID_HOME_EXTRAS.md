# Android 推荐位与公告

2026-10-06 接入既有匿名 `GET /home/recommendations` 与 `GET /home/announcements`；接口和数据库结构不变。

Compose → HomeExtrasViewModel → HomeExtrasUseCases → RemoteHomeExtrasRepository → Retrofit；复用已有限时、无重定向、响应预算拦截器，单响应最多 64 KiB。首页本地目录立即显示，推荐/公告独立后台获取。5 分钟前台刷新间隔，进行中的请求去重；两个接口单独保留最近成功结果，失败显示重试，成功空数组清空旧内容。

推荐位最多 20 条，工具目标仅显示当前注册中心内已启用的工具，目录快照变化时重新过滤；链接仅允许 HTTPS 且无账号凭据，经用户点击并看到目标链接后在浏览器打开。文本用 Compose 原生 Text 渲染；外部 imageUrl 仅校验，不加载，避免额外追踪请求。公告最多 5 条，INFO/WARNING/CRITICAL 级别，点击查看完整纯文本，标题/正文/标识/重复项/长度严格校验。

中英资源、共享主题/加载复用。不持久化公告/推荐，不保存已读状态，不主动显示推送；离线保留当前进程最近成功内容，重启后等待刷新。

4 项 Repository 回归覆盖真实 DTO 映射、非法/重复目标、HTTPS/凭据限制、条数/公告级别与 HTTP 失败。Android 199 项测试、assembleDebug/lintDebug 在 2 分 17 秒通过；设备 UI 与实际管理员配置联调按用户要求留待发布。
