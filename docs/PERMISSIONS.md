# Android 权限说明

2026-10-06 核对源码与 Debug 合并清单；Release 合并清单在发布构建中复核。

| 权限 | 来源/用途 | 用户控制 |
| --- | --- | --- |
| INTERNET | 工具目录、账号/同步、公告推荐、反馈、AI、官网 WebView 和用户主动网络诊断；普通权限，无授权弹窗 | 可匿名使用基础工具，云同步默认关闭，AI 提交前单独同意 |
| CAMERA | 二维码实时扫描；运行时敏感权限 | 点击开启相机才申请，拒绝后仍能用生成/相册识别；可在系统权限设置撤回 |
| ACCESS_NETWORK_STATE | 合并依赖清单引入的普通权限，查询网络可用性 | 不等同于位置权限，不读取 Wi-Fi 密码 |
| com.cangshuo.toolbox.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION | AndroidX 自动声明的应用内 signature 级接收器保护 | 不向用户申请，不用于读取其他应用数据 |

未声明定位、麦克风、通讯录、电话、READ_MEDIA_*、READ/WRITE_EXTERNAL_STORAGE 或 MANAGE_EXTERNAL_STORAGE。传感器指南针/水平仪不申请位置；相机为可选硬件，缺失设备可安装并使用其他工具。

图片/PDF 输入使用系统 Photo Picker/文档选择器授予的单个 URI；保存用 MediaStore（新建本应用结果）或 ACTION_CREATE_DOCUMENT，分享用 FileProvider 临时只读 URI，不获取全盘权限。二维码 Wi-Fi/联系人/日历仅生成载荷，导入由用户选择的第三方应用处理，不由本应用直接修改通讯录、日历或网络。

Release 网络安全配置禁止明文 HTTP；Debug 只开放既有 loopback 开发地址。WebView 文件表单仅允许 pdf_studio 白名单来源，不开放文件 URL、JS 原生接口或任意来源文件访问。

App 我的页提供内置中英权限说明，无需联网。发布时检查 APK/AAB 合并清单与第三方 SDK，若新增权限必须同步本说明与隐私政策。没有广告或第三方自动分析 SDK。

Android assembleDebug/lintDebug 1 分 14 秒成功，内置中英说明与官网 `/permissions/` 已创建；没有新增运行时权限。依据：[Android 运行时权限](https://developer.android.com/training/permissions/requesting)、[SAF 文档访问](https://developer.android.com/training/data-storage/shared/documents-files)。设备授权/拒绝/撤回与真实 provider 验收延期；源码清单核对不替代运行时验收。
