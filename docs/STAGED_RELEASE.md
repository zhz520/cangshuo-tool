# 灰度发布与回滚

2026-10-06 完成 Phase 7「灰度发布」。单主机上的发布分三步：构建版本化镜像 → 在预发 Compose 项目上启动并跑冒烟 → 通过后把同一批镜像切到正式项目再跑冒烟。任何一步失败都不会把未验证的版本留在正式环境；正式冒烟失败会尝试自动回到上一个已记录版本。

## 组成

| 文件 | 作用 |
| --- | --- |
| `deploy/release/release.sh` | `preflight` / `build --tag` / `stage --tag` / `rollback` / `status` |
| `scripts/smoke_release.py` | 对单个 API（可选官网）执行发布冒烟：健康、目录、推荐、公告、限流头、注册/登录/刷新/登出/注销一次性账号、官网页面、可选 CORS |
| `deploy/docker-compose.yml` | 镜像名支持 `SERVER_IMAGE`、`WEB_IMAGE`、`RELEASE_TAG` 覆盖，默认行为不变 |

镜像约定：构建产物为 `<RELEASE_REPO>-api:<tag>` 与 `<RELEASE_REPO>-web:<tag>`（默认 `cangshuo-toolbox-release`）。预发和正式加载同一批镜像，避免“预发过了但正式换了包”。

环境变量：`PROD_PROJECT`（必填）、`STAGING_PROJECT`（默认 `<PROD>-staging`）、`PROD_ENV_FILE`（默认 `../.env.production`）、`STAGING_ENV_FILE`（默认 `../.env.staging`）、`PROD_COMPOSE_FILES`、`STAGING_COMPOSE_FILES`、`RELEASE_REPO`、`STATE_FILE`、`PYTHON`、`STAGING_API_URL`、`STAGING_WEB_URL`、`PROD_API_URL`、`PROD_WEB_URL`、`CORS_ORIGIN`、`KEEP_TAGS`。URL 未显式设置时脚本会从 env 文件的 `LOCAL_*_PORT` 或 `WEB_DOMAIN`/`API_DOMAIN` 推导。

`preflight` 校验两个 Compose 配置、项目名互异、磁盘剩余 ≥ 2 GiB，并在使用 `.env.production` 时检查 `deploy/certs/<域名>/{fullchain,privkey}.pem` 是否存在。

## 发布流程

```sh
cd /opt/cangshuo-toolbox/deploy
export PROD_PROJECT=cangshuo-toolbox-production
export STAGING_PROJECT=cangshuo-toolbox-staging
sh ./release/release.sh preflight
sh ./release/release.sh build --tag 0.2.0
sh ./release/release.sh stage --tag 0.2.0
```

`stage` 会：启动预发项目（`--wait --wait-timeout 300`）→ 预发冒烟（失败则停预发、正式不动）→ 正式项目切换镜像并等待健康 → 正式冒烟 → 写 `release-state/state.env`（`CURRENT_TAG`、`PREVIOUS_TAG`、时间、镜像名）。加 `--prune-images` 会按 `KEEP_TAGS`（默认 3）清理更旧的发布镜像，当前/上一个版本永不删除。

回滚：

```sh
sh ./release/release.sh rollback
sh ./release/release.sh status
```

回滚只切换应用镜像，`CURRENT_TAG` 与 `PREVIOUS_TAG` 互换，回滚后同样跑正式冒烟。

数据库迁移是 Flyway 前向迁移：应用回滚不会撤销已执行的迁移。发布前必须确认新迁移对旧镜像兼容（只加表/字段/索引，或先发布向后兼容代码）；不兼容时按 [生产备份与恢复](BACKUP.md) 用备份恢复，而不是硬改 schema。

Android 客户端由商店控制灰度：先在 Google Play/其他商店以 1%–5% 起步，观察崩溃诊断（App 内「崩溃诊断」+ 商店 vitals）与 API 错误率后逐步放量；服务端发布与商店放量是两个独立环，任何一环异常都先停住该环。

## 验证

`scripts/check_staged_release.py` 在本机 Compose 环境执行真实演练：`preflight` → 构建 `drill-1` → 启动预发栈并冒烟 → 记录状态 → （用镜像重打标签模拟第二次构建）`drill-2` 再走一遍预发+正式 → `rollback` 回到 `drill-1` → `status` → 正式冒烟。结束后关闭预发项目（含卷）、把本机正式项目恢复到原始镜像并删除临时镜像与状态。

2026-10-06 演练结果：11/11 通过。演练验证了预发先行的顺序、状态记录、回滚切换和冒烟脚本真实通过；没有覆盖真实证书续期、真实域名解析和商店灰度放量，这些按上线当天操作执行。

查阅日期 2026-10-06：[Compose `up --wait`](https://docs.docker.com/reference/cli/docker/compose/up/)、[Play 分阶段发布](https://support.google.com/googleplay/android-developer/answer/6346149)。
