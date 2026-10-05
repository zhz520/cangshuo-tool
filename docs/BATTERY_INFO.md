# 电池信息

2026-10-05，battery_info / DEVICE / LOCAL；Compose → BatteryInfoViewModel → ObserveBatteryInfoUseCase → AndroidBatteryInfoRepository，V23 新增目录元数据。无新权限、库、网络或持久化。

## 功能与边界

电池存在、电量、充电/放电/满电/已连接未充电、AC/USB/无线/扩展坞来源、系统健康、温度、电压、技术、瞬时电流、剩余电荷与省电模式。页面前台订阅 ACTION_BATTERY_CHANGED 与省电广播，首次读 sticky Intent，手动刷新重新订阅；退后台/离页注销，合并待处理快照，无后台轮询。中英资源、主题和共享加载，整份复制/分享只由用户操作。

level/scale 必须存在且 scale>0、0≤level≤scale；缺失不是 0%。温度 hasExtra 后换算十分之一摄氏度，支持负温度并限制 −100…200°C，电压保留正 mV。BatteryManager 属性 Int.MIN_VALUE 为未提供，电流保留原始正负，µA→mA、µAh→mAh；数值转换不做整数乘法。厂商未按约定报告时不能据此保证充放方向。健康枚举不代表寿命/健康百分比，charge counter 是剩余电荷而非设计容量；不估算续航、循环次数、充满时间或电池寿命。

未知电源位/状态/健康值明确未提供；单属性异常降级，广播注册或读取失败结束加载，可重试并保留旧结果；注册成功的 receiver 在取消时释放。报告只含固定字段、技术字符串最多 512 字符；取消不发布新快照，不打印系统或用户原始数据。没有电池的设备显示真实 present=false。

## 实际源码参考

2026-10-05 阅读 [Expo BatteryModule.kt](https://github.com/expo/expo/blob/main/packages/expo-battery/android/src/main/java/expo/modules/battery/BatteryModule.kt) 与 [BatteryStateReceiver.kt](https://github.com/expo/expo/blob/main/packages/expo-battery/android/src/main/java/expo/modules/battery/BatteryStateReceiver.kt)：sticky 广播获取、状态映射、前后台注册/注销和防重复订阅。也读了 [react-native-device-info RNDeviceModule.java](https://github.com/react-native-device-info/react-native-device-info/blob/master/android/src/main/java/com/learnium/RNDeviceInfo/RNDeviceModule.java) 的 getPowerStateFromIntent。以上是可变化分支，未复制源码或引入 Expo/React Native；本项目用 callbackFlow/awaitClose 和页面生命周期，在已有比例逻辑上补零 scale/缺失/越界校验，缺失不伪造 0。属性单位和不支持值核对 [Android BatteryManager](https://developer.android.com/reference/android/os/BatteryManager)。

## 验证

新增 2 项电量非法比例/Int 极值、负温度、属性 sentinel 回归；174/174 Android 测试、assembleDebug、lintDebug 通过（1 分 52 秒），Lint 0 错误/28 既有警告。Server 镜像成功（测试跳过），V23 success=1，8081/8088 total=15，battery_info/DEVICE/LOCAL 无需登录。按用户要求延后实际插拔充电、厂商数据、生命周期和复制分享手机验收。
