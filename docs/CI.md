# 持续集成

工作流位于 `.github/workflows/ci.yml`，使用 GitHub Actions。推送、Pull Request 和手动 `workflow_dispatch` 都会触发；不使用路径过滤，保持总门禁状态稳定。同一 Git 引用的新运行会取消旧运行。

## 门禁与产物

四个独立任务并行执行，最后由名为 `CI` 的任务汇总。任何任务失败、取消或跳过，总门禁都不会成功。

| 任务 | 执行内容 | 产物 |
| --- | --- | --- |
| Android build and lint | `:app:assembleDebug :app:lintDebug` | Debug APK、Lint HTML/XML 报告 |
| Server package | Maven Wrapper `package -Dmaven.test.skip=true` | 可运行 API JAR |
| Admin typecheck and build | `npm ci`、`npm run build`，含 TypeScript 检查 | `dist/`，使用正式 API 地址与 `/admin/` 路径 |
| Workflow and deployment configuration | actionlint、Shell 语法、本地/正式 Compose 配置校验 | 无 |

产物在对应运行的 Artifacts 中保留 7 天。Debug APK 用于开发安装，发布签名、AAB、部署及镜像发布按 Phase 7 完成。

当前门禁覆盖构建、静态检查与配置。服务端显式跳过测试；没有新增或运行自动化测试。单元测试、设备运行、业务集成、数据库迁移演练与公网部署不属于此次验证结果。

## 工具链

- 托管运行器固定 `ubuntu-24.04`，不跟随 `ubuntu-latest` 切换系统。
- Android/Server 使用 Temurin JDK 21。Android 保持 AGP `9.1.0`、Gradle Wrapper `9.3.1`、API 36 和 Build Tools `36.0.0`，工作流确保对应 SDK 包存在。
- Gradle 分发包新增官方 SHA-256 校验；setup-gradle 同时校验官方 Wrapper JAR。现有引导 JAR 的校验值与官方 Gradle `9.2.0/9.2.1` 相同，它启动配置中的 `9.3.1` 分发包；本次没有替换 JAR 或升级项目依赖。
- Server 保持 Maven Wrapper `3.9.11` 及既有分发包校验值。
- Admin 使用 Node `22.23.3`，与部署构建镜像的 Node 版本一致；`npm ci` 按锁文件安装。
- actionlint 固定 `1.7.12`，下载后核对 Linux 归档 SHA-256，再运行工作流检查。

## Action 锁定

2026-10-03 从各官方仓库的 Release/tag 解析到提交 SHA。工作流使用完整 SHA，注释记录可读版本；更新时同时核对源码输入参数和运行器兼容性。

| Action | 版本 | 固定提交 |
| --- | --- | --- |
| actions/checkout | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| actions/setup-java | v6.0.1 | `de7274f081f381c8f8158605e0321c36c376e2e6` |
| gradle/actions/setup-gradle | v6.4.0 | `3f5f9adaf7d9fecd50b5935e54106014257a94e6` |
| actions/setup-node | v7.0.0 | `820762786026740c76f36085b0efc47a31fe5020` |
| actions/upload-artifact | v7.0.1 | `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` |

## 配置与缓存

工作流只申请 `contents: read`，checkout 不保留 Git 凭据。使用普通 `pull_request` 事件。当前构建不需要仓库 Secret，不读取实际部署 `.env`、证书或签名密钥。

Gradle 使用 GitHub 缓存的 `basic` 模式，PR 只读缓存；Maven 缓存按 `server/pom.xml` 更新；npm 缓存按 `admin/package-lock.json` 更新，并通过 `NPM_CONFIG_CACHE` 对齐项目 `.npm-cache`。缓存只保存构建依赖，产物上传限定 APK、Lint 报告、JAR 与 `dist/`。

部署配置任务显式读取公开 `.env.example`，为四个必需密码变量提供仅供配置解析的占位值。它只执行 `config --quiet`，不启动容器、不打印解析后的环境配置，也不使用这些值部署服务。本地配置同时覆盖可选 storage profile，正式配置不要求真实证书存在即可进行语法解析。

## 本地复现

在对应目录执行以下门禁。Windows 使用 `.bat`、`.cmd` Wrapper；其他平台可用 `sh gradlew`、`sh mvnw`。

Android：

```powershell
.\gradlew.bat --no-daemon --console=plain :app:assembleDebug :app:lintDebug
```

Server：

```powershell
.\mvnw.cmd -B -ntp -Dmaven.test.skip=true package
```

Admin（先设置公开构建变量 `VITE_APP_BASE_PATH=/admin/`、`VITE_API_BASE_URL=https://toolapi.zhzgo.cn/api/v1`）：

```powershell
npm.cmd ci --no-audit --no-fund
npm.cmd run build
```

actionlint 从官方固定 Release 下载并核对归档校验值后，在仓库根目录执行 `actionlint .github/workflows/ci.yml`。部署配置的完整命令及占位环境变量见工作流中的 `deployment` 任务；它与本地实际启动命令用途不同。

## 本地运行记录

2026-10-03：

- actionlint `1.7.12` Windows 归档的 SHA-256 校验通过，工作流检查退出码为 0；Git Bash 对四份 Shell 脚本执行 `sh -n` 通过。
- Gradle `9.3.1` 分发包校验值已从官方读取；现有 Wrapper JAR 与官方 `9.2.x` 校验值相符。Windows JDK `21.0.9` 执行 Debug 构建和 Lint 成功，APK 已生成，Lint 为 0 错误、6 警告，沿用原有 SDK/版本提示及应用图标待完善项。
- Server 在临时源码副本通过 Maven `package -Dmaven.test.skip=true`，13 个 Java 源文件编译并生成可运行 JAR；现有 API 预览持续运行。
- Admin 在临时源码副本使用 Linux Node `22.23.3` 完成 `npm ci`、TypeScript 检查及 Vite 正式构建。产物使用 `/admin/` 资源路径与 `https://toolapi.zhzgo.cn/api/v1`。
- 两套 Compose 使用 CI 占位环境变量及 `.env.example` 校验通过，没有启动 CI 容器环境或读取部署凭据。
- 没有执行自动化测试或 GitHub 托管工作流。本机 Windows JDK/Android 构建、Linux Alpine 后台构建与 `ubuntu-24.04` 托管环境存在差异，首次远程运行仍需确认。

## GitHub 首次运行

用户于 2026-10-03 指定 [zhz520/cangshuo-tool](https://github.com/zhz520/cangshuo-tool) 为项目仓库，`origin` 已关联其 HTTPS 地址，默认分支为 `main`。首次托管 CI 运行待推送后确认；完成本地门禁不表示远程 CI 已绿色。

关联用户确定的 GitHub 仓库并推送项目源码后，在 Actions 中查看 `CI` 的四个任务与总门禁。首次绿色后，再将分支保护的必需检查设为 `CI`；工作流不能代替仓库分支保护设置。手动触发要求工作流已位于默认分支。

## 实现依据

- [GitHub Actions 工作流语法](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)
- [Ubuntu 24.04 托管运行器工具清单](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)
- [Gradle Actions 参数](https://github.com/gradle/actions/blob/3f5f9adaf7d9fecd50b5935e54106014257a94e6/setup-gradle/action.yml)
- [Gradle 9.3.1 分发包校验值](https://services.gradle.org/distributions/gradle-9.3.1-bin.zip.sha256)
- [actionlint 1.7.12 Release](https://github.com/rhysd/actionlint/releases/tag/v1.7.12)
