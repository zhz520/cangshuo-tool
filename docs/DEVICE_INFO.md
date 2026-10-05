# 设备信息

2026-10-05，Phase 4 第一项，device_info / DEVICE / LOCAL。Compose → DeviceInfoViewModel → ReadDeviceInfoUseCase → AndroidDeviceInfoRepository，注册到共享 ToolRegistry，V21 只新增目录元数据并保留管理员原记录。

## 功能与范围

- 离线显示 21 项：制造商、品牌、型号、设备/产品代号、硬件平台、SoC、Android/API、安全补丁、系统构建、CPU ABI、本进程可用逻辑处理器、系统报告总/可用 RAM、开机时长、默认显示模式像素/刷新率、逻辑密度、OpenGL ES 和工具箱版本。
- 打开/回到前台自动刷新，也可手动刷新；整份文本复制与系统分享，字段文字可选择。复用主题与共享加载，中文/英语同步，操作行支持换行。
- SoC 公开字段需 API 31，早期系统显示“系统未提供”；内存/显示服务缺失与单字段异常不影响其他信息。RAM 使用 1024 进制 B/KiB/MiB/GiB，不将逻辑处理器误称物理核心；elapsedRealtime 含休眠，显示模式像素不是当前窗口尺寸，逻辑 dpi 不是面板真实物理 dpi，刷新率是当前模式而非最大支持频率。
- 不读取序列号、IMEI、Android ID、用户设备名或账户资料；无新增权限、库或网络上传。只在用户点击时复制/分享。根权限检测、基于指纹猜测模拟器、温度/CPU 实时频率/GPU 厂商和硬件跑分不在本项范围。

## 边界

读取在 IO 调度器，单个 UI 请求串行，取消不发布结果；失败退出加载并保留上一份数据供重试。系统空字符串/unknown 显示未提供，控制字符替换为空格，超长字段 512 字符后标 …；ABI 最多取 16 项，标准 Android 常见值为 1–4 项。输出信息限固定字段，超长字段标记截断。负字节/时间不可显示，缺失内存服务不伪造 0 总 RAM，当前可用内存需在总量范围内；分享应用缺失和剪贴板异常显示固定本地化失败。

## 源码参考

2026-10-05 完整阅读 [Expo DeviceModule.kt 具体业务源码](https://github.com/expo/expo/blob/main/packages/expo-device/android/src/main/java/expo/modules/device/DeviceModule.kt)（main 为可变化分支）。采用其 Build/ABI/ActivityManager 公开信息获取思路；本项目改为 Compose + UseCase/Repository、本机按需刷新、缺失服务降级。没有引入 Expo/React Native/YearClass，未采用其用户设备名读取、Root/模拟器推断与物理尺寸分类；显示模式查询补充使用 Android DisplayManager，CPU 数为本进程可用逻辑处理器。未复制第三方源码。

## 验证

Android 170 项测试、assembleDebug、lintDebug 通过，Lint 0 错误/28 既有警告（1 分 52 秒）。Server 镜像构建通过（测试跳过）；本地 Flyway 成功记录 21，8081/8088 两个目录接口均返回 total=13，device_info 为 LOCAL、无需登录。没有真机读取/多窗口/分享/剪贴板验收，按用户要求延后；不宣称硬件信息已在手机上核对。
