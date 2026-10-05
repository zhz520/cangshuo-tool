# 发布构建与上线准备

运营主体：湖北花海网络科技有限公司；联系邮箱：14329140@qq.com。应用 ID `com.cangshuo.toolbox`，当前候选版本 `0.1.0` / versionCode `1`。OCR 按用户要求延期，AI 默认关闭；外部兼容 API 的地址、模型、密钥和服务商说明由部署方以后配置。

## 自动构建

`.github/workflows/release.yml` 在 main 推送或手动执行时运行 Release 单元测试、Lint、APK/AAB 构建和产物门禁。main 推送默认未签名，不自动上传商店或部署服务器。工作流 Actions 沿用固定 SHA、JDK 21 / AGP 9.1 / API 36，无依赖升级。产物、SHA-256 清单、测试和 Lint 报告保留 14 天。

AGP 9 默认只为 tested build type 创建单元测试；本项目显式启用 Release 测试。AAB 关闭语言拆分，让中文/英文都随安装保留，支持 App 内切换。Release 禁用调试、只允许 HTTPS，没有 debug 回环例外；未指定上传密钥时不退回调试签名。

本机在 `android/`：

```powershell
$env:JAVA_HOME='D:\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME=Join-Path $PWD '.gradle'
$env:TEMP=Join-Path $PWD '..\temp\build'
$env:TMP=$env:TEMP
$env:TMPDIR=$env:TEMP
.\gradlew.bat --offline --console=plain -PtoolboxVersionCode=1 -PtoolboxVersionName=0.1.0 :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease
```

在仓库根运行：

```powershell
python scripts/check_release_artifacts.py --sdk D:\Android\sdk
```

本机产物保存在 D 盘项目 `android/app/build/outputs/`：Release APK、`bundle/release/app-release.aab` 和 `release-readiness.json`。未签名 APK 不能安装，未签名 AAB 不能提交商店。GitHub 托管运行器是 Linux，工作目录/临时目录由平台提供，不宣称重定向到 D 盘。

门禁实际检查 APK/AAB ZIP 完整性、Release manifest 权限、关闭备份/调试、HTTPS 常量与 debug 地址排除、网络配置无明文例外、64 位 native ELF PT_LOAD 16 KB 对齐、未压缩 APK native 条目 16 KB ZIP 对齐。32 位 ELF 仅校验格式；真实 16 KB 设备运行仍需发布验收。保存文件大小/SHA-256，不把源码检查等同于设备验证。

## 上传签名

本机四个环境变量必须一起配置，否则构建报错：`TOOLBOX_KEYSTORE`（D 盘私有文件绝对路径）、`TOOLBOX_STORE_PASSWORD`、`TOOLBOX_KEY_ALIAS`、`TOOLBOX_KEY_PASSWORD`。不要把值写进 Gradle 属性、Git、截图或聊天。JKS/keystore/p12 被 Git/Docker 忽略；上传密钥由所有者妥善保存，备份保留权限并放在独立故障域。

GitHub 的 `release` Environment 可配置保护规则及四个 Secrets：`TOOLBOX_UPLOAD_KEY_BASE64`、`TOOLBOX_STORE_PASSWORD`、`TOOLBOX_KEY_ALIAS`、`TOOLBOX_KEY_PASSWORD`。手动工作流勾选 signed=true 才解码密钥、设置环境并签名；运行完通过 trap 清理临时密钥。没有密钥时签名运行失败，不产生可上传的成功结论。

签名门禁加 `--require-signed`，执行 apksigner/jarsigner 检查并拒绝 Android Debug 证书。生成/导入正确上传证书、登记 Play App Signing 和商店提交属于所有者的发布步骤；项目不自动生成一个未知身份的生产密钥。

参考查阅日期 2026-10-06：[Android 签名](https://developer.android.com/studio/publish/app-signing)、[16 KB 页要求](https://developer.android.com/guide/practices/page-sizes)、[AGP 9 默认测试行为](https://developer.android.com/build/releases/agp-9-0-0-release-notes)。

2026-10-06 实际验证：Release 213/213 JUnit 通过、Lint 0 错误/18 警告、APK/AAB 构建通过（3 分 47 秒）；产物门禁通过，未配置真实上传密钥，signatureVerified=false。actionlint 1.7.12（官方 Windows ZIP SHA-256 校验后）验证 CI/Release 两份工作流通过。真实上传证书验证和商店接收尚未执行。
