# 指南针

2026-10-05，compass / SENSOR / LOCAL，Compose → CompassViewModel → ObserveCompassUseCase → AndroidCompassRepository，V25 元数据。无定位/新权限、新库或上传；缺失硬件仍可打开并看到不支持状态。

## 功能与边界

- 罗盘指针、0≤磁北方位角<360°、八方位、系统精度状态、可选系统角度误差、数据来源。刷新、暂停/继续和整份复制/分享，中文/英语与共享主题/加载。
- 优先 TYPE_ROTATION_VECTOR；回退加速度计 + 磁场计并以 0.2 低通系数过滤，缺少组合则不支持。采样 100ms、最多 10Hz、conflate，监听跨重启由 Mutex 串行；前台订阅、后台/离页/暂停注销，复制 SensorEvent 所需值，不保留可复用数组。
- 默认显示屏的 0/90/180/270° rotation 对应 remapCoordinateSystem，角度表示当前屏幕顶部相对磁北。要求屏幕朝上、尽量平放；俯仰/翻滚超过 60° 不输出角度并提示平放。外接显示屏、多窗口非默认屏与竖握导航不在本项范围。
- 不用 GPS/磁偏角，不标注真北；不采用 GAME_ROTATION_VECTOR 作为磁北来源。精度枚举显示高/中/低/不可靠，不捏造固定 ±15/30/45°；仅当旋转向量确有第五项且有限、非负才转换弧度角误差。低精度提示远离磁物、8 字动作，但不显示假校准成功。
- 旋转向量至少 3 项，矩阵/角度非有限值无效；回退必须有两类样本且都在 1s 内，矩阵不可计算时不显示零度。等待首个结果 3s 后给重试文字，监听仍可接收后续数据。注册失败和系统异常固定反馈，无堆栈或原值日志。

## 实际源码参考

2026-10-05 阅读 [flutter_compass FlutterCompassPlugin.java 完整方向业务源码](https://github.com/hemanthrajv/flutter_compass/blob/master/android/src/main/java/com/hemanthraj/fluttercompass/FlutterCompassPlugin.java)：旋转向量/加速度磁场回退、低通、单调限频、显示屏 rotation 与矩阵映射、注册注销。也阅读 [compassx CompassXPlugin.java](https://github.com/natsuk4ze/compassx/blob/main/android/src/main/java/studio/midoridesign/compassx/CompassXPlugin.java) 的可选第五项角误差和真北转换。以上均为可变化分支。本项目不读取位置/推断真北，不将精度枚举换为假定角误差；仅按页面生命周期订阅所需传感器，以明确平放范围替代竖握方向自动模式，增加数组长度、无效矩阵和缺样处理。原生架构实现，未引入 Flutter 或复制源码。

## 验证

新增 2 项角度 wrap、非有限值、八方位边界回归；176/176 Android 测试、assembleDebug、lintDebug 成功（1 分 56 秒），Lint 0 错误/28 既有警告。Server 镜像成功（测试跳过），V25 success=1，两条本地目录路由 total=17，compass/SENSOR/LOCAL。真机方向/磁干扰/校准/横竖屏准确度按用户要求延后，编译不代表已核对指南针精度。
