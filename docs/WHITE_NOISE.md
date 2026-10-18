# 白噪音

## 定位

- 工具编码：`white_noise`
- 分类 / 模式：`LIFE` / `LOCAL`
- 排序：635
- 数据迁移：`V83__seed_white_noise_tool.sql`
- 分层：Compose → `NoiseViewModel` → `NoiseRepository` → 本地设置与媒体前台服务

在 Android 设备上实时合成白、粉、褐噪音，以及雨声、海浪和风扇环境声。六层可以独立开启、调节音量并混合播放；提供专注、雨夜、睡眠场景和 0/15/30/60/90/180 分钟睡眠定时。工具不包含录音素材、网络请求、账号、云同步或服务端音频处理。

## 开源参考

查阅日期：2026-10-18。以下实现只用于核验算法与 Android 播放生命周期，本项目按现有分层重新实现，没有复制源码或资源。

| 项目 | 固定版本与许可 | 实际核验源码 | 采用内容与适配差异 |
| --- | --- | --- | --- |
| [chmouel/noise-timer](https://github.com/chmouel/noise-timer) | [`eff4e724fba11ca46bd681873da79746b8fe027c`](https://github.com/chmouel/noise-timer/commit/eff4e724fba11ca46bd681873da79746b8fe027c)；MIT | [NoiseSynthesis.kt](https://github.com/chmouel/noise-timer/blob/eff4e724fba11ca46bd681873da79746b8fe027c/app/src/main/kotlin/com/chmouel/noisetimer/NoiseSynthesis.kt)、[NoiseEngine.kt](https://github.com/chmouel/noise-timer/blob/eff4e724fba11ca46bd681873da79746b8fe027c/app/src/main/kotlin/com/chmouel/noisetimer/NoiseEngine.kt)、[NoiseService.kt](https://github.com/chmouel/noise-timer/blob/eff4e724fba11ca46bd681873da79746b8fe027c/app/src/main/kotlin/com/chmouel/noisetimer/NoiseService.kt) | 核验了 Paul Kellet 粉噪音、泄漏积分褐噪音、跨缓冲区 DSP 状态、独立 `AudioTrack` 播放线程、单调时钟睡眠计时、结束淡出和会话 generation。本项目扩展为六层实时混音，使用软限幅与约 180 ms 启停增益渐变，并把睡眠最后 15 秒作为可测试的播放策略；没有照搬其单声景结构。 |
| [surendranb/chromatone](https://github.com/surendranb/chromatone) | [`275f5045cbb648914fde0c74657fe4be674df08d`](https://github.com/surendranb/chromatone/commit/275f5045cbb648914fde0c74657fe4be674df08d)；MIT | [NoiseGenerator.kt](https://github.com/surendranb/chromatone/blob/275f5045cbb648914fde0c74657fe4be674df08d/app/src/main/java/com/fuseforge/chromatone/audio/NoiseGenerator.kt)、[NoisePlayer.kt](https://github.com/surendranb/chromatone/blob/275f5045cbb648914fde0c74657fe4be674df08d/app/src/main/java/com/fuseforge/chromatone/audio/NoisePlayer.kt)、[NoiseForegroundService.kt](https://github.com/surendranb/chromatone/blob/275f5045cbb648914fde0c74657fe4be674df08d/app/src/main/java/com/fuseforge/chromatone/NoiseForegroundService.kt) | 仅参考多个噪音类型和前台服务状态展示。其滤波器随缓冲区重置、睡眠计时按秒递减、终止时没有淡出，且最小缓冲区单位处理存在混用风险，本项目没有采用这些做法；也没有复制其 MediaSession 或界面。 |

## 已实现功能

- 六种声音：白噪音、粉噪音、褐噪音、雨声、海浪和风扇。每一层使用独立随机源与滤波状态，开启或关闭不会重置其他层。
- 多声混音：按各层音量求和，用平方根权重控制峰值，经过软限幅后输出 44.1 kHz、16 位、双声道 PCM。
- 实时合成：每 1024 帧生成一次，不下载、不录制、不循环播放音频文件，因此没有素材首尾拼接声，也不增加音频资源体积。
- 三种场景：专注（褐噪音 + 风扇，60 分钟）、雨夜（雨声 + 海浪，30 分钟）、睡眠（粉噪音 + 褐噪音，90 分钟）；应用场景会同步层音量、总音量和定时。
- 播放控制：播放、暂停、继续、停止；暂停保留剩余定时并冻结倒计时，停止清空定时与播放状态。
- 睡眠定时：关闭、15、30、60、90、180 分钟。运行中调整定时会立即重算单调时钟截止点；最后 15 秒线性淡出，避免突然截断。
- 设置持久化：各层音量、总音量和定时选择保存在应用私有 SharedPreferences；清除应用数据或卸载会删除设置。
- 前台媒体服务：常驻播放状态通知、暂停/继续与停止按钮；任务管理器可以识别当前媒体播放用途。
- 音频焦点：申请 `AUDIOFOCUS_GAIN`；永久或临时失焦时暂停并释放焦点，继续播放前重新申请。耳机拔出或其他输出设备断开时同样自动暂停。
- 通知权限：Android 13+ 在工具页按需展示权限卡片；拒绝通知不阻止音频播放和媒体前台服务，用户仍可从任务管理器管理服务。
- 中英文、深色主题、系统大字体、纵向滚动、窄屏换行和 TalkBack 标签。
- 场景使用即时动作语义；每个声音开关和音量滑块都包含具体声音名称，而不是重复的“声音音量”。

## 输入、DSP 与边界

- 层音量、总音量为 `0..1`；NaN 归一化为 0，越界值收敛到边界。至少保留一层有效声音才能开始播放。
- 定时只接受界面列出的选项；非法 Intent 值回退到当前已保存设置。定时上限 180 分钟，不使用精确闹钟权限。
- 计时使用 `SystemClock.elapsedRealtime()`，不受用户调整墙钟或时区影响；暂停时计算一次剩余毫秒并冻结截止点。
- 每层状态在缓冲区间连续保存。雨滴为稀疏随机脉冲衰减，海浪使用约 0.065 Hz 起伏，风扇叠加约 58 Hz 低频正弦；混合后使用 `tanh` 软限幅，输出保持在 PCM Short 范围。
- 单音频线程负责全部写入，避免多个 `AudioTrack` 同时输出。新的暂停、继续或场景切换会取消旧 ticker 并用 generation 隔离，旧会话不能结束或改写新会话。快速暂停后继续复用同一音频引擎；运行状态和退出检查串行化，避免旧线程退出与新线程启动重叠。已经报告失败的引擎实例会被拒绝复用，下一次恢复必须创建新的音轨和线程。
- 音频设备在初始化、写入或播放中消失时通过回调报告失败，服务撤下前台通知、释放音频焦点和 `AudioTrack`，页面显示“当前无法启动音频输出”或对应的一次性错误；服务销毁时停止 ticker、音频线程和音频焦点，注销耳机断开广播。
- 没有麦克风、录音、存储、精确闹钟、后台联网或账号权限，也不采集播放明细或睡眠数据。
- 当前限制：不提供单独左右声道平衡、均衡器、后台下载素材、MediaSession 外部耳机按键、锁屏媒体控制器、小组件、自定义声音上传或跨设备同步。

## 并发、错误与恢复

- `startForegroundService()` 只用于用户明确开始播放。服务在申请音频焦点前立即进入媒体前台状态，满足 Android 前台服务时限。
- 通知事件先校验当前状态：停止后的暂停事件不会复活服务，继续事件只恢复真正的暂停会话。
- 系统因资源压力终止服务后，`START_STICKY` 会从应用私有服务偏好恢复 `PLAYING`、`PAUSED` 或停止状态；播放中的单调截止时间也会持久化，恢复时只使用剩余时间，已经到期则直接停止，不会重复完整定时。用户主动停止写入 `IDLE`，通知权限被拒不会阻止媒体服务。
- 音频焦点拒绝时不显示虚假的播放状态，清理前台通知并停止服务；从暂停继续时若焦点被拒，页面保留暂停状态并显示音频不可用错误。通知暂停或继续失败不会改写持久化音量和层配置。
- 开始、暂停、继续和停止都更新同一进程内的 `NoisePlaybackRuntime`，页面只观察状态，不直接持有 `AudioTrack` 或 Service。

## 界面与可访问性

- 页面顶部显示混音内容、播放状态、剩余时间、播放/暂停/继续与停止操作。
- 场景使用可换行的即时动作 Chip，定时使用可选中 Filter Chip；六层声音位于带分隔线的设置分组中，只有已启用层显示音量滑块。
- 所有按钮和滑块使用共享 `ToolboxButton`、`ToolboxOutlinedButton`、`ToolboxSwitch`、主题色和形状令牌；界面不直接控制音频对象。
- 总音量与层音量滑块提供语义标签；状态不只依赖颜色。页面使用最大表单宽度和滚动内容，适配窄屏、2 倍字体与深色主题。

## 验证范围

定向 JUnit **7/7** 覆盖定时选项与边界、层开关、场景归一化、六种合成器的有限值/幅度/非静音、混音确定性、总音量静音、单调计时截止点、秒数向上取整、15 秒淡出边界和 ViewModel 操作委托。

Android Debug/Release 各 **796/796**（119 个测试套件），Debug/Release Lint 均 **0 错误 / 91 条既有警告** 且白噪音源码、资源与测试无 finding；Debug APK **25,631,975** 字节、Release APK **17,332,477** 字节、AAB **16,747,960** 字节，Release artifact 门禁通过。服务端在 JDK 21 下 **226/226**、V83 静态契约 **143/143**，后台 `vue-tsc --noEmit` 与生产构建通过。

Android 16 模拟器验证了六种声音的服务注册与混音运行、场景切换到褐噪音 + 风扇和 60 分钟定时、播放/暂停/继续/停止、通知拒绝时继续播放、授权后真实媒体通知、通知栏暂停/继续/停止、最后一层关闭立即停止、系统杀进程后的播放与暂停恢复（暂停剩余时间保持冻结），以及深色主题、2 倍字体和 720×1600 窄屏。窄屏验收发现并修复了主音量标题与百分比重叠。进程重启日志、媒体前台服务状态、通知类别/动作和 `logcat` 未发现异常。

没有等待真实 15 分钟来主观试听淡出；截止点、最后 15 秒乘数和音量平滑由 JUnit 与实际短周期播放链路共同覆盖。音频焦点被其他应用抢占、耳机拔出广播和真机扬声器质感仍列为后续设备验收，静态代码路径已核对。

## 后续验收清单

- Android 16 模拟器逐层播放六种声音，验证混音、暂停/继续/停止、15 秒定时与淡出。
- 验证 Android 13+ 通知权限允许和被拒两条路径、真实媒体前台通知及通知栏控制。
- 验证音频焦点被其他应用占用、耳机断开、返回页面和进程重启后的可见状态。
- 验证深色、2 倍字体、窄屏和 TalkBack；低端设备持续播放功耗与真机扬声器质感需另行评估。
