# 图片取色器验收进度（2026-10-17）

本轮只完成 `color_picker`、V58 元数据、后台联动及发布；接续前轮初步实现，修复 bounds 模式 null 返回被误判为无效图片、透明通道输出、采样舍入/预算、EXIF 与取消/重试。双语资源、图标与 ToolRegistry 一致。

## 分工

| 角色 | 范围 | 状态 |
| --- | --- | --- |
| 主线程 | Android 图片加载、领域/状态回归、UI、文档、候选与发布 | 门禁、模拟器、生产发布与只读验收完成 |
| Halley | `check_color_picker_runtime.py`、`verify_color_picker_deploy.py` | 两份文件已整合，离线 35/35；父线程已运行真实隔离候选 608/608；子智能体未提交或部署 |
| Kierkegaard | 图片加载、坐标、取消与状态的独立代码复核 | 发现高位深 PNG 预算偏低、Compose 整数对齐偏差；主线程已修复并加 3 项回归；无文件改动、提交或部署 |

## 已取得证据

- Android 最终 **560/560**，APK 与 Lint 通过，**0 错误 / 81 既有警告**，取色器无 Lint 发现；取色器 19 项回归。AndroidX EXIF 1.4.2 显式声明，版本与现有 CameraX 传递依赖相同。
- 服务端 **226/226**，候选镜像 `sha256:db4e1cc9437736135926874ac95e54531fd4fd5afd51a2cf291eb84b5bddcc72`。
- 临时 MySQL/API **608/608**：后端目录、后台管理/权限/编辑校验、状态与软删除、幂等 seed、热门、每日/累计和 7/30/90 天统计；临时容器、用户、库已清理。
- 上线前只读生产状态：仓库 `32e9024`，Flyway **57/57、失败 0**，简繁互转启用，5 个生产容器 healthy。

独立复核后补修：预算按可能的 RGBA_F16 每像素 8 字节而非 ARGB_8888 偏好估算，并给旧预览保留固定份额；触摸与定位圈复用 BitmapPainter 的整数宽高与居中偏移，独立计算 x/y 比例。新增高位深内存/不同旧图大小、301px 奇数视区、非正方形取样 3 项回归，最终 Android **560/560**、APK 和 Lint 通过。

## 模拟器与发布

Android 16 AVD 实际验证：

- 系统照片选择器加载 4,097 × 103 测试 PNG，明确显示取样图 1,024 × 25 与缩小提示；像素实测 `#FF8800`、`rgb(255, 136, 0)`、`hsl(32, 100%, 50%)`、坐标 `x 500 · y 12`。
- 800 × 400 半透明 PNG 显示 `#FF870080`、`rgba(255, 135, 0, 0.502)`、`hsla(32, 100%, 50%, 0.502)`；原输入绿色为 136，Android 预乘/反预乘舍入后为 135，符合文档中“取样基于解码 sRGB 像素”的边界。点「Copy HEX」有带真实值的复制反馈，逐像素按钮使坐标从 `x 400 · y 200` 变为 `x 401 · y 200`。
- 无效 JPG 显示可恢复错误并保留此前图像；重新选择有效 PNG 后清除错误。
- 方向 6 的 JPEG 旋正后显示 `Original 400 × 800 · Sample 400 × 800`，原先 800 × 400 的轴正确交换。新图清空此前取样；点击留白不生成结果，红色区域取样为 JPEG 解码值 `#FE0000`。
- 360dp 宽、1.5 倍字体、深色模式下保留图片、结果与坐标；尺寸说明自然换行，复制、分享、四个微调按钮及色板均能滚动到达，截图无横向挤出。显示设置随后恢复。
- 真实 RGB16 PNG（2048 × 2048、方向 6、41,697 字节）按内存预算降至 1024 × 1024 后成功加载，中央及最左边界均实测 `#FF8800`；边界坐标 `x 0 · y 512` 可选中，证明整数对齐修正没有把原图像素误当留白。截图保存在 `color-picker-16bit-edge.png`。

截图保存在 `android/build/calc-emulator/color-picker-alpha.png`、`color-picker-rotated.png`、`color-picker-narrow-dark.png`、`color-picker-narrow-dark-result.png`、`color-picker-16bit-edge.png`（忽略的构建证据目录，不进入 Git）。

实现提交 `237afad` 已推送 GitHub 并由生产仓库 fast-forward 同步；直接将已通过的候选镜像推广，未重新构建来源。

- 生产 API 镜像 `sha256:db4e1cc9437736135926874ac95e54531fd4fd5afd51a2cf291eb84b5bddcc72`，旧 API 保留为 `before-color-picker-v58` 回滚标签；Flyway **58/58、失败 0**，`color_picker` 为 `ENABLED / LOCAL / sort 105`。
- 线上 HTTPS 只读验收 **107/107**：公开目录、双语元数据、后台权限/详情/筛选/编辑选项、7/30/90 天统计与排行均一致；未写入合成使用量、未修改线上配置。5 个生产容器 healthy。

调试 APK 为 `android/app/build/outputs/apk/debug/app-debug.apk`，部署不自动更新用户手机。真机、TalkBack、低端硬件耗时与所有图片 provider 不视为已验证。
