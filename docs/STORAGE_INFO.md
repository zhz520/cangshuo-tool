# 存储信息

2026-10-05，storage_info / DEVICE / LOCAL，Compose → StorageInfoViewModel → ReadStorageInfoUseCase → AndroidStorageInfoRepository。V22 新增目录记录，保持既有管理员记录。复用主题、加载、可选择结果与报告复制/系统分享组件，中文和英语同步。

## 功能与边界

- 内部数据卷和系统公开的非主模拟存储卷：卷容量、已分配、空闲、应用可用与保留字节，挂载/只读/不可访问状态、可移除属性。前台/手动刷新，不扫描文件、统计媒体分类、清理或删除。
- StatFs 的 used=total−free，reserved=free−available，单位 B/KiB/MiB/GiB。数据卷不等于设备标称闪存，不相加 root/data 分区。计数必须满足 total>0、0≤available≤free≤total；异常或不一致显示未提供，不伪造零容量。
- 主模拟共享存储与内部数据卷不重复计数；其他可获得文件系统 UUID 的卷去重。UUID 无法获取时不会声称不同条目容量可相加，不显示 UUID/路径。最多读取 16 个公开卷，截断或部分失败明确提示。
- API 30+ 使用 StorageVolume.directory；API 26–29 通过应用专属 externalFilesDirs 匹配卷，系统可能建立应用专属目录。未挂载或无可访问目录显示未提供；单卷异常保留其他结果。并非任意 USB/SD 分区都可读，无新增存储权限或库。
- IO 调度、有界查询、串行刷新、取消不发布结果；全局失败保留旧结果并提供刷新。剪贴板/分享异常显示固定反馈，不输出堆栈，不上传报告。

## 实际源码参考

2026-10-05 阅读 [react-native-device-info RNDeviceModule.java 的容量/空闲业务方法](https://github.com/react-native-device-info/react-native-device-info/blob/master/android/src/main/java/com/learnium/RNDeviceInfo/RNDeviceModule.java)，包括 getTotalDiskCapacitySync、getFreeDiskStorageSync、容量乘法与异常降级（master 为可变化分支）。采用 StatFs 长整型计数思路；本项目不采用 root+data 相加，防止把分区或同卷重复当作物理容量，也不用 double 保存原始字节。补充 [Android StatFs 字段语义](https://developer.android.com/reference/android/os/StatFs)与公开 StorageVolume/StorageManager 查询；按 Android 原生架构实现，未引入 React Native 或复制第三方代码。

## 验证

新增 2 项计数一致性、Long 极值和保留空间回归，172/172 Android 测试通过；最终 test/assemble/lint 通过（1 分 31 秒），Lint 0 错误/28 既有警告。Server 镜像成功（测试跳过），V22 success=1，8081/8088 total=14，storage_info/DEVICE/LOCAL 无需登录。按用户要求跳过手机/可移除介质/复制分享验收，没有声称已核对真机容量。
