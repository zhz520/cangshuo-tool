# 图片取色器（color_picker）

分类 IMAGE、模式 LOCAL、排序 105；V58 只登记目录元数据，后台工具管理、使用量、热门榜及统计沿用已有接口。图片内容不发送服务端，不新增权限。客户端安装包含该原生工具的新 APK 后才可执行。

## 参考与取舍

2026-10-17 实际阅读以下业务源码：

| 来源 | 采用思路与适配差异 |
| --- | --- |
| [Madrapps/EyeDropper：EyeDropper.kt](https://github.com/Madrapps/EyeDropper/blob/master/eyedropper/src/main/java/com/madrapps/eyedropper/EyeDropper.kt)，blob `e8da0ff3d0f7b4da8e2f8c855fd7ce7f144b0cfc`，[Apache-2.0 许可](https://github.com/Madrapps/EyeDropper/blob/master/LICENSE) | 触摸位置通过图像变换映射回像素，再读取颜色。本工具用 Compose ContentScale.Fit 的逆变换，忽略留白区；包含 x/y=0 边缘像素，且不把越界结果混作透明黑。使用自身实现，未复制源码或引入旧 View/DrawingCache 依赖。分支链接可变化，blob 是文件哈希而非 commit。 |
| [Android 大位图加载](https://developer.android.com/topic/performance/graphics/load-bitmap)、[BitmapFactory.Options](https://developer.android.com/reference/android/graphics/BitmapFactory.Options) | 先 bounds 后按预算采样；`inJustDecodeBounds` 返回 null 是正常行为，检查 outWidth/outHeight。显式请求 sRGB / ARGB_8888，防止硬件位图无法逐像素读取。 |
| [AndroidX PainterModifier](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/draw/PainterModifier.kt)、[BitmapPainter](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/painter/BitmapPainter.kt)、[Skia SkAndroidCodec](https://github.com/google/skia/blob/main/src/codec/SkAndroidCodec.cpp)（2026-10-17 复核） | 将 Fit 后的宽高取整、再居中对齐，按实际整数绘制宽高分别逆映射 x/y，避免奇数视区边缘偏移；高位深 PNG 可忽略 ARGB_8888 偏好而输出 RGBA_F16，因此预算按每像素 8 字节计算，而非假设一定 4 字节。分支链接可变，未复制源码。 |
| 项目 `feature/imagecompress/data/LocalImageCompressRepository.kt` 及 `feature/color/data/LocalColorRepository.kt` | 沿用输入校验、互斥原生任务、EXIF 翻转/旋转和内存预检思路；格式输出直接复用颜色转换的 RGB/HSL 逻辑，不重复公式。EXIF 使用项目已由 CameraX 引入的 AndroidX 1.4.2，并显式声明依赖，未升级版本。 |

子智能体另阅读了 [skydoves/ColorPickerView.java](https://github.com/skydoves/ColorPickerView/blob/main/colorpickerview/src/main/java/com/skydoves/colorpickerview/ColorPickerView.java) 的 `onTouchReceived/getColorFromBitmap`，用于检查坐标和目录能力说明，未复制实现。

## 功能与流程

- 系统照片选择器导入 JPG、PNG、WebP，不索取相册广泛访问权限；取消选择保留当前结果。
- 图片预览支持点选、拖动与定位圈，忽略 Fit 留白；提供左/右/上/下 1 像素按钮及辅助功能“取图片中心颜色”操作。
- 展示原图与采样图尺寸，缩小后明确提示；坐标为已经旋正的采样位图零基像素坐标。
- HEX、RGB、HSL 复制；颜色文本分享；24 色去重取样记录、色板复制、点击回看与清空。
- 不透明 HEX 为 `#RRGGBB`；透明 HEX 为 CSS 顺序 `#RRGGBBAA`，RGBA/HSLA 保留透明度，Alpha 小数保留 3 位。HSL 依已有转换工具：色相取整、百分比最多 1 位小数。
- 加载、错误、取消、重试、选择器不可用有双语反馈；失败保留之前成功的图像，成功换图清空旧采样记录。
- Compose → ViewModel → UseCase → Repository。UI 不解码图片、不访问网络或 Room；唯一 `color_picker` 注册使搜索、收藏、最近使用沿用平台流程。

## 输入与资源边界

| 项目 | 行为 |
| --- | --- |
| 来源 | 仅 `content://`，通过一次临时快照确保 bounds、EXIF 和 decode 读取相同数据 |
| 文件大小 | 读取中计数，最多 32 MiB；不信任 provider 声明长度 |
| 源尺寸 | 每边最多 100,000，最多 500,000,000 像素；解码前拒绝超限 |
| 采样 | 2 的幂次降采样，预估向上取整，最长边不超过 2,048 |
| 内存 | 每份预览按最坏 RGBA_F16 的 8 字节/像素，单份占总预算扣除 8 MiB 余量后的最多 1/3；覆盖当前解码与旋转重叠、旧预览尺寸不同的场景；总限额 `min(64 MiB, heap/4)`，不足继续降采样，OOM 映射为恢复提示 |
| 方向 | AndroidX EXIF 处理 1–8 的旋转及镜像，轴交换同步反映在尺寸中 |
| 并发与取消 | Repository Mutex 串行快照/原生解码；拷贝每块检查取消，解码/旋转前后检查；新输入递增任务编号，旧任务不得更新结果。取消时清理未交付位图与快照 |
| 临时文件 | App 私有 cache/color_picker 内，读取成功、失败或取消都在 finally 删除输入快照；成功位图由当前 ViewModel/Compose 持有，不强行 recycle 仍可能在绘制的图片 |
| 手势边界 | 与 Compose 的整数目标尺寸及居中对齐一致，x/y 独立比例；非有限坐标、无效视区、越界、留白返回空；精确微调不越出图像边界 |

## 当前限制

- 图片取色；相机实时取色尚未实现。
- 动画仅取首帧；不提取所有帧。GIF、RAW 和 SVG 不支持。HEIF 不列为支持类型。
- 取样基于 sRGB 解码像素；缩小图的细节可能变化，非原文件压缩字节或高动态范围通道的无损分析器。透明像素经 Android 预乘解码可能失去不可见 RGB 信息。
- 仅当前页面生命周期内保留图片与色板；旋转配置变化保持 ViewModel，进程死亡后需重新选图，URI 不写入 SavedStateHandle 或持久存储。
- 取消不能强制打断底层同步 codec 或阻塞 provider；通过 Mutex 和前后取消点控制并发与归属。

## 实际验证

2026-10-17 最终 Android **560/560**、APK 与 Lint 通过，Lint **0 错误 / 81 既有警告**，取色器文件无发现；取色器领域/状态测试 **19 项**，覆盖 CSS 透明色、字母/百分比格式、高位深与旧预览预算、尺寸上限、Compose 整数对齐、非正方形双轴映射、边缘像素、NaN、色板、取消/重试/失败状态。已使用 AndroidX EXIF 1.4.2 执行最终门禁，CI 加入取色器静态注册与目录契约检查，actionlint 通过。

服务端 **226/226**，真实隔离 MySQL/API 候选 **608/608**：V58、元数据、普通账号/匿名权限、后台 CRUD/status/软删除、migration 重放保留人工配置、热门及每日/累计/7/30/90 天统计；临时数据库、用户和容器已清理。`--static` **35/35** 检查双语文案、占位符、唯一注册、图标及 seed，不能替代 Android 图片解码验收。

模拟器和生产发布的最终记录见 [取色器验收进度](COLOR_PICKER_ACCEPTANCE_2026-10-17.md)。不得将候选测试等同于已发布，也不声称覆盖所有设备或图像格式。
