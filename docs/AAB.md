# Android 发布候选包

2026-10-06，versionName 0.1.0、versionCode 1、应用 ID com.cangshuo.toolbox。本机发布候选产物在 D 盘项目 android/app/build/outputs 下；不把二进制或密钥提交 Git。

| 产物 | 字节 | SHA-256 |
| --- | --- | --- |
| apk/release/app-release-unsigned.apk | 14,411,330 | d1a8ea50f57dfc7c40381d276e1aedd8d1face5cfab6e467293b76013d132cdc |
| bundle/release/app-release.aab | 14,018,521 | 2e85c86a49b5da0f249f4be240c9a4638d5b5b63d0c7b96ab5578f989bfb8c6f |

Release 213 项测试全部通过，Lint 0 错误/18 警告。APK/AAB 各 16 个 native 条目，64 位 ELF 与 APK 未压缩条目 16 KB 对齐检查通过；正式 HTTPS、权限、备份和不可调试检查通过。AAB 保留中英文资源，关闭语言拆分。完整构建/门禁/签名说明见 [RELEASE.md](RELEASE.md)。

这些是未签名构建产物，不能直接安装/上传商店。后续用所有者上传密钥重新构建，执行 --require-signed 检查，注册正确证书，并在商店/设备验证。再次构建的时间、签名及版本信息会改变哈希；以随该次构建生成的 release-readiness.json 为准。没有进行真实商店上传或 16 KB 设备验证。
