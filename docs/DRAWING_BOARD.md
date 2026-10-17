# 画板

## 定位

- 工具编码：`drawing_board`
- 分类 / 模式：`LIFE` / `LOCAL`
- 排序：628
- 数据迁移：`V76__seed_drawing_board_tool.sql`
- 分层：Compose → `DrawingBoardViewModel` → `DrawingBoardRepository`

在 3:4 白色画布上自由手绘，支持颜色、粗细、橡皮擦、撤销重做和 PNG 保存/分享。笔画只在应用本机处理，不联网、不登录、不申请存储或相机权限。

## 开源参考

查阅日期：2026-10-17。

| 项目 | 固定版本与许可 | 实际核验源码 | 采用内容与适配差异 |
| --- | --- | --- | --- |
| [akshay2211/DrawBox](https://github.com/akshay2211/DrawBox) | `5707c88e5f53402745ed961564e07904cae8118e`；Apache-2.0 | [State.kt](https://github.com/akshay2211/DrawBox/blob/5707c88e5f53402745ed961564e07904cae8118e/DrawBox/src/commonMain/kotlin/io/ak1/drawbox/domain/model/State.kt)、[Element.kt](https://github.com/akshay2211/DrawBox/blob/5707c88e5f53402745ed961564e07904cae8118e/DrawBox/src/commonMain/kotlin/io/ak1/drawbox/domain/model/Element.kt)、[DrawBoxController.kt](https://github.com/akshay2211/DrawBox/blob/5707c88e5f53402745ed961564e07904cae8118e/DrawBox/src/commonMain/kotlin/io/ak1/drawbox/presentation/viewmodel/DrawBoxController.kt) | 参考自由笔迹的采样点模型、撤销/重做快照、橡皮擦与 PNG 无界面导出边界。本项目按现有轻量工具架构自行实现固定 3:4 Compose 画布、笔画归一化坐标、8 色画笔、24dp 粗细、白色橡皮擦、30 步会话历史和 1200×1600 PNG 导出；没有引入 DrawBox 依赖，也没有复制其手势、形状、文本、图片、SVG 或 JSON 实现。 |

## 已实现功能

- 3:4 纵向白色画布，支持轻点圆点和连续拖动笔迹，使用二次曲线平滑采样点。
- 8 种笔刷颜色和 1–24dp 粗细；调节颜色时自动退出橡皮擦。
- 橡皮擦复用白色笔迹，所见即导出结果。
- 撤销、重做和清空；清空本身可撤销，新笔画会清空重做栈。
- 当前画作写入 `SavedStateHandle`，旋转屏幕或短暂离开工具后保留。
- 导出白底 1200×1600 PNG，并按界面卡片的 16dp 圆角比例裁剪，使角落笔迹与预览一致；Android 10+ 直接写入相册，旧系统使用系统“创建文档”；分享使用缓存文件和 FileProvider。
- 中文与英文资源、独立 24dp 线性图标、颜色名称、粗细滑块语义和操作的无障碍标签。
- 5×5“无障碍网格绘制”模式：关闭触摸手绘后，读屏或键盘用户可逐个选择单元格放置笔点。

## 输入与边界

- 每次落笔最多采样 1000 个点，相邻点距离小于画布宽高归一化距离 0.0015 时合并；总点数最多 8000，总笔画最多 80。
- 笔画坐标限制在 `0..1` 的画布范围内；宽度按画布宽度归一化，导出时按 1200px 等比还原。
- 撤销历史最多 30 个文档快照，只存在于当前 ViewModel 会话；进程被回收后恢复当前画作和圆角比例，但不恢复撤销/重做栈。
- PNG 先完整生成到受控缓存，再写入 MediaStore 或 SAF 目标；只有本应用新建的 MediaStore 记录会在失败时删除，不删除用户通过系统文档选择器指定的原文件。
- 分享临时文件在仓库初始化和每次分享前清理超过 24 小时的旧文件；文件名包含毫秒，避免快速重复分享覆盖；离开工具会取消未完成的保存/分享并清除一次性分享请求。
- 不提供缩放/平移、图层、矩形/圆形/文字工具、压感、倾斜、透明画布、SVG/JSON、云同步或跨设备历史。
- 不申请存储权限；PNG 限制 12MB、导出 1,920,000 像素，避免一次性大图内存压力。

## 验证范围

定向 JUnit 13 项覆盖点/笔宽边界、文档预算、文件名、增删与撤销重做、清空恢复、画笔/橡皮擦/网格设置、SavedState 损坏恢复、保存/分享任务取消和一次性事件清理。

2026-10-17 Android 16 模拟器（1080×2424）实测：通过 `draw` 搜索打开画板；连续笔迹、换色、橡皮擦、撤销/重做、清空、5×5 无障碍网格、深色主题、2 倍字体、PNG 保存与分享选择器均正常。实际导出为 **1200×1600 RGBA PNG**，白底且包含笔迹；文件位于系统相册的 `Pictures/CangshuoToolbox`。Android 8/9 的 SAF 目标写入未在设备上执行；真机、TalkBack 全流程、低端设备连续绘制帧率仍未验收。

本地门禁：Android 全量 **712/712**，`assembleDebug` 成功，Lint **0 错误 / 88 条警告**（新增画板源码与资源无 finding）；服务端 **226/226**；V76 离线静态契约 **75/75**；后台类型检查与生产构建沿用通过基线。APK 为 **24,107,533** 字节。

2026-10-17 发布验收：严格隔离候选 **247/247** 通过，使用一次性 Docker 网络，仅连接生产 MySQL 容器与临时 Redis；未继承生产应用网络或第三方凭据。生产只读 HTTPS 验收 **146/146**；生产镜像 `sha256:f017298bc56460ec4cac02db37c15b8042185b4f87a705421db59b0adf98a371`，Git 回滚标签 `before-drawing-board-v76`，上一镜像保留为 Docker 标签 `cangshuo-toolbox-production-api:before-drawing-board-v76`。Flyway 为 **76/76** 且全部成功，五个容器 healthy，官网、后台登录页和 API 健康接口均返回 200。
