# 传感器

2026-10-05，sensor_info / SENSOR / LOCAL；Compose → SensorsViewModel → SensorsUseCases → AndroidSensorsRepository。V24 注册目录，无新权限、依赖、上传或历史保存。

## 功能与边界

- 系统传感器清单最多 128 项：名称、厂商、类型/版本、最大量程、分辨率、声明功耗、最小采样间隔、唤醒属性。功耗是厂商声明值，不是实时耗电。
- 选择一个传感器看读数，暂停/继续、前台恢复，复制/分享清单与当前快照。实时范围包括加速度、陀螺仪、磁场（及未校准项）、光线、气压、接近、重力、线性加速度、温度、相对湿度、三种旋转向量；其他类型只看元数据。不采集心率、运动步数等需额外权限类型。
- 原始 Android 单位：加速度 m/s²、角速度 rad/s、磁场 µT、光照 lx、气压 hPa、距离 cm、温度 °C、相对湿度 %。旋转向量按索引显示，没有角度伪标签；加速度等三轴 X/Y/Z 是设备固定坐标，横屏时不会改称屏幕坐标。未校准项后续索引是偏差，向量的可选项按设备提供显示。
- 一次一个监听，采样请求 100 ms、输出最多 10 Hz、conflate；基于 event.timestamp 单调时间限频，不受改系统时间影响。值最多 16 项，非有限值未提供、超过上限明确截断，不持有复用 SensorEvent 数组。系统可能低于请求频率；on-change 传感器保留最近一次值，等待首条时明确提示，没有伪造零读数。
- 页面离开/退后台/暂停/换选择时注销。注册失败（包括厂商权限）、无服务、空清单分别反馈；旧监听取消后不能更新新选择。刷新清单按新索引重新选择，动态插拔需刷新。未做高频日志、波形历史、文件导出或动态传感器自动注册。

## 实际源码参考

2026-10-05 阅读 [Expo SensorProxy.kt](https://github.com/expo/expo/blob/main/packages/expo-sensors/android/src/main/java/expo/modules/sensors/SensorProxy.kt)、[SensorSubscription.kt](https://github.com/expo/expo/blob/main/packages/expo-sensors/android/src/main/java/expo/modules/sensors/SensorSubscription.kt)、[AccelerometerModule.kt](https://github.com/expo/expo/blob/main/packages/expo-sensors/android/src/main/java/expo/modules/sensors/modules/AccelerometerModule.kt) 与 [GyroscopeModule.kt](https://github.com/expo/expo/blob/main/packages/expo-sensors/android/src/main/java/expo/modules/sensors/modules/GyroscopeModule.kt) 的实际事件转换/订阅/前后台释放逻辑（main 可变化）。采用按需监听和结果节流；改用 callbackFlow/awaitClose、硬件单调时间、明确 100ms 采样；不申请 HIGH_SAMPLING_RATE_SENSORS，不采用其墙钟节流或加速度除重力转 g 的显示方式。原生架构实现，未引入 Expo 或复制源码。本机 Android SDK 确认仅使用公开 Sensor API。

## 验证

Android 174 项既有回归、assembleDebug、lintDebug 成功（1 分 48 秒），Lint 0 错误/28 既有警告；Server 镜像成功（测试跳过），V24 success=1，两条本地目录路由 total=16，sensor_info/SENSOR/LOCAL 无需登录。没有新增模拟系统硬件的单元测试。按用户要求延后真实传感器单位、精度、暂停/后台及横竖屏验收。
