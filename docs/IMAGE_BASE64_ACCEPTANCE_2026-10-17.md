# 图片 Base64 验收记录（2026-10-17）

本轮完成独立工具 `image_base64`、V59 目录与 Android/服务端联动。图片内容、输入 Base64 和导出文本不上传服务端；服务端仅保存目录元数据并复用在管后台、使用计数和统计。

## 分工

| 角色 | 范围 | 状态 |
| --- | --- | --- |
| 主线程 | Android 实现、资源/UI、JVM 回归、模拟器、门禁、候选与发布 | 门禁、模拟器和候选完成；生产推广中 |
| Lovelace | V59、静态/隔离验收脚本、工具文档初稿 | 已整合；未提交或部署 |
| McClintock | Android 只读独立复核 | 发现 1 个高、3 个中和 1 个低风险问题；主线程均修复/收紧，未改动文件 |

## 实现证据

- Android `testDebugUnitTest` **572/572**，0 失败、0 错误、0 跳过；取色器任务后新增本工具 12 项专项回归。
- `assembleDebug` 与 `lintDebug` 通过，Lint **0 错误 / 81 既有警告**；新增源码/资源无 Lint 发现。
- 服务端 `mvn test` **226/226**；静态目录检查 **42/42**。
- 隔离 MySQL/API 候选 **185/185**：V59、公开/后台 DTO、迁移重放、编辑/状态/软删除、每日/累计计数、热门榜和统计窗口；临时资源已清理。
- 实际阅读 DevToys `Base64ImageDataTypeDetector.cs` 与 IT-Tools `base64-file-converter.vue` / `base64.ts`，采用文件字节优先、Data URL 校验和尺寸限制思路；记录见工具文档。

## 模拟器证据

Android 16 AVD，1080 × 2400：

- 系统照片选择器加载 138,683 字节 PNG，显示 `image/png`、原图 1080 × 2400、预览 540 × 1200；完整 Data URL 为 184,934 字符，与回读原图重新编码的长度、前缀和结尾一致。
- 复制完整结果成功，Android 显示剪贴板浮层。
- 输入 91 字符 URL-safe、无填充的 1 × 1 PNG，成功显示 68 原始字节和 1 × 1 预览，输出规范化为带填充标准 Data URL。
- 关闭 Data URL 后 URL-safe 和换行选项可用；开启后输出为 92 字符且确实在第 76 字符后换行。
- 通过系统文档选择器保存到 Downloads，回读 `decoded.png.base64.txt` 为 114 字符，与完整输出一致；分享生成 `.txt` 并成功打开系统分享面板。

截图保存在被忽略的 `android/build/calc-emulator/image-base64-*.png`。

## 复核修复

- 二进制上限由 8 MiB 收紧为 4 MiB，文本上限 600 万字符；换行不再构造分块列表，Data URL/换行使用预估容量单缓冲。
- 渲染与 Repository 捕获 OOM；输出重算期间禁用复制/保存/分享；重算失败时清空旧输出。
- 换图、清空、模式切换会取消旧保存/分享任务，并用结果版本阻止旧任务回写分享面板或成功提示；保存/分享互斥。
- 超长粘贴显示 `TOO_LARGE`，禁止继续解码旧输入；解析、Base64 解码和 Bitmap 解码前后加入协程取消检查。底层同步 codec 不能强制中止，仍存在有限等待边界。

## 生产发布

生产发布结果待回填。调试 APK 为 `android/app/build/outputs/apk/debug/app-debug.apk`；服务端目录更新不自动安装新 APK。真机、TalkBack、低内存设备、所有文件 provider 与全部图片 codec 仍未验收。
