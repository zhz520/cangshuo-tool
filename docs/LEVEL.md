# 水平仪

2026-10-05，level / SENSOR / LOCAL；Compose → LevelViewModel → ObserveLevelUseCase → AndroidLevelRepository，V26 新增目录。无新权限、库、声音/振动、网络或持久化。

## 功能与边界

- 屏幕双轴倾角、绝对倾斜角、圆形气泡与文字状态，暂停/继续、刷新、相对归零/复位、复制分享。0.5° 为本工具近零提示阈值，不代表传感器测量精度。画布气泡在 ±10° 范围内移动，越界停在边缘；角度文本保留实际结果，没有伪造限幅角度。
- 优先 TYPE_GRAVITY；缺失时加速度计以 0.15 低通系数近似重力。采样与发布最多 10Hz、conflate，固定 3 个值，监听跨重启串行；退后台、离页、暂停注销，重开等待新样本。
- 0/90/180/270° 默认显示屏旋转映射屏幕 X 向右、Y 向上；atan2 与 hypot 算双轴，acos 算绝对倾斜，有限值和重力模长 5…15 m/s² 校验。自由落体、明显异常/缺数据不给零角度。要求屏幕朝上，背面朝上不显示近零气泡或可归零成功。运动/加速度会影响近似结果；外接显示屏、侧边测量/自动边缘模式与专业仪器校准未实现。
- 相对归零仅把当前 X/Y 作为本次 ViewModel 会话基准，绝对倾斜角继续显示重力推导的值；转屏后自动清基准。复位回到绝对双轴读数，无永久校准或“校准成功”承诺；进程重启不保存基准。
- 3s 未收到结果后退出加载、提示平放/重试，但仍接收后续样本。系统精度低/未知显示文字，缺硬件/注册失败独立反馈。报告有限固定字段，没有原始异常日志。

## 实际源码参考

2026-10-05 阅读 [vTechGIS/BubbleLevel OrientationProvider.java](https://github.com/vTechGIS/BubbleLevel/blob/master/app/src/main/java/org/woheller69/level/orientation/OrientationProvider.java) 和 [Orientation.java](https://github.com/vTechGIS/BubbleLevel/blob/master/app/src/main/java/org/woheller69/level/orientation/Orientation.java)，包括传感器注册释放、显示方向映射、基准保存/扣除和水平阈值。浏览工具取源码失败后，通过 GitHub 官方 contents API 取得并完整阅读；所读文件 blob 分别 b7f0cbf9cdedf07d905029ba33977c01ecac5afd、5b4c7a2808b1be9e62b171490121779881cbf0fc（blob 为文件标识，非仓库 commit）。master 可变化。

采用方向映射、明确基准和近零判定思路；本项目以公开重力向量的 atan2/hypot/acos 独立实现，不使用该项目伪磁向量矩阵、全局 Activity、五种侧边模式或持久化校准。仅研究思路，没有复制 GPL 源码或引入该依赖。前台释放同时沿用先前阅读的 Expo SensorSubscription 生命周期思路。

## 验证

新增 2 项已知 30° 倾斜/四种 rotation/相对基准，以及反面/自由落体/非有限值/极值回归；178/178 Android 测试、assembleDebug、lintDebug 成功（1 分 51 秒），Lint 0 错误/28 既有警告。Server 镜像成功（测试跳过），V26 success=1，8081/8088 total=18，level/SENSOR/LOCAL。实际精度、气泡移动、转屏归零/生命周期和手机验收按用户要求延后。
