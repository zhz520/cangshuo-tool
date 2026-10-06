# 生产备份与恢复

2026-10-06 完成 Phase 7「生产备份」。备份覆盖 MySQL 全库（含账号、云同步、反馈、AI 次数、删除台账），删除台账另有独立快照；启用对象存储时可选归档 MinIO 数据卷。备份默认保留 30 天，与隐私政策一致；台账快照默认保留 180 天，因为它是防止已删除账号被恢复的最后凭证，需要更长留存并复制到独立故障域。

## 工具与格式

- `deploy/backup/backup.sh`：从 `deploy/` 目录运行，在 Docker 主机或 Git Bash 上执行。要求 `mysql` 服务正在运行。
- `deploy/backup/restore.sh`：恢复必须显式传入 `--confirm`，会先停止 API 容器，导入完成后自动等待健康检查通过。
- 加密：优先 `openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -md sha256 -salt`；脚本会先探测宿主 OpenSSL 能力，1.0.2 等老版本自动回退到 `-aes-256-cbc -salt`（同一台机器加解密一致），口令只从 `BACKUP_PASSPHRASE_FILE` 读取；脚本从不打印口令或明文数据。
- 产物：`toolbox-<UTC时间>.tar.gz.enc`（`mysql.sql`、`ledger.tsv`、`manifest.txt`、可选 `minio-data.tar.gz`）与同名 `.sha256`；台账快照为 `ledger/ledger-<UTC时间>.tsv.enc` 与 `.sha256`。
- `manifest.txt` 记录 schema 版本、表数量、台账行数、dump/台账 SHA-256 与是否包含 MinIO 归档。

环境变量（全部可选，除口令文件外）：`COMPOSE_ENV_FILE`（默认 `../.env.production`）、`COMPOSE_FILES`、`BACKUP_DIR`、`BACKUP_RETENTION_DAYS`（30）、`LEDGER_RETENTION_DAYS`（180）、`LEDGER_DIR`（`$BACKUP_DIR/ledger`）、`INCLUDE_MINIO`、`MINIO_VOLUME`（默认 `${COMPOSE_PROJECT_NAME}-minio 卷`，注意 Compose 卷名为 `项目名_minio-data`）、`MINIO_HELPER_IMAGE`（默认 `nginx:1.30.5-alpine3.24`，仅用于打包数据卷）、`OPENSSL`。

## 定时任务

建议每日一次全量备份、每 15 分钟一次台账快照，并强制把 `BACKUP_DIR`（尤其是 `ledger/`）同步到与生产主机不同的存储。示例 cron（口令文件权限 `600`，路径按实际部署调整）：

```cron
17 2 * * * cd /opt/cangshuo-toolbox/deploy && COMPOSE_ENV_FILE=../.env.production BACKUP_DIR=/var/backups/cangshuo-toolbox BACKUP_PASSPHRASE_FILE=/etc/cangshuo-toolbox/backup.pass LEDGER_DIR=/var/backups/cangshuo-ledger sh ./backup/backup.sh >> /var/log/cangshuo-backup.log 2>&1
*/15 * * * * cd /opt/cangshuo-toolbox/deploy && COMPOSE_ENV_FILE=../.env.production BACKUP_DIR=/var/backups/cangshuo-toolbox BACKUP_PASSPHRASE_FILE=/etc/cangshuo-toolbox/backup.pass LEDGER_DIR=/var/backups/cangshuo-ledger sh ./backup/backup.sh --ledger-only >> /var/log/cangshuo-backup.log 2>&1
30 3 * * * rsync -a --delete /var/backups/cangshuo-ledger/ backup-vault:/cangshuo/ledger/
```

脚本会自行删除超出保留期的旧文件（只匹配 `toolbox-*.tar.gz.enc` 与 `ledger-*.tsv.enc` 及其 `.sha256`）。备份口令单独保管，不能与备份放在同一故障域；丢失口令等同丢失备份。

## 恢复流程

```sh
cd /opt/cangshuo-toolbox/deploy
BACKUP_PASSPHRASE_FILE=/etc/cangshuo-toolbox/backup.pass sh ./backup/restore.sh \
  --backup /var/backups/cangshuo-toolbox/toolbox-20261006T020017Z.tar.gz.enc --confirm --minio
```

步骤：校验 `.sha256` → 解密并解包 → 停止 API 容器 → 导入 `mysql.sql` → 合并最新删除台账（删除已注销账号并补齐台账行）→ 可选恢复 MinIO 数据卷 → 启动 API 并等待健康检查。`--ledger <file>` 指定台账快照，缺省取 `LEDGER_DIR`（默认备份目录下 `ledger/`）里最新的 `ledger-*.tsv.enc`。

安全约束：

- 只允许 `--confirm` 明确触发的恢复；脚本没有交互式确认，适合在变更单中执行。
- 台账为空时必须显式加 `--allow-empty-ledger`，防止用损坏或未同步的台账放开已删除账号；完全跳过合并必须显式 `--skip-ledger`。
- 恢复会整体回滚到备份时刻：备份之后写入的非账号数据（反馈、同步、AI 计数）同样回退；执行前先确认最新台账已导出（`--ledger-only`）并保留现场备份。
- Redis 只保存限流计数等短生命周期数据，不需要备份；Nginx/Compose 配置来自 Git，随代码回滚。
- 恢复旧备份后必须保留最新删除台账的合并结果，发布前用 `SELECT COUNT(*) FROM deleted_account` 抽查。

## 验证

`scripts/check_backup_restore.py` 在本地 Compose 栈上执行真实演练：注册四个一次性账号 → 删除一个 → 生成加密全量备份（含 MinIO 卷标记文件）→ 改昵称、备份后再删除一个账号、再注册一个 → 导出更新的独立台账 → 恢复 → 断言备份前状态回到、备份后新增账号消失、全部已删账号保持注销（含备份后才删除的）、MinIO 标记文件恢复。结束后删除全部一次性账号、台账行、标记文件与临时目录。

2026-10-06 演练结果：22/22 通过（详见当日提交记录）。演练同时确认脚本在密钥文件、校验和、空行/CRLF 处理与 Compose 环境下的实际行为；本机不启用真实 cron，生产 rsync 目标与口令分发按部署环境另行配置。

查阅日期 2026-10-06：[mysqldump 8.4 手册](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html)（`--single-transaction` 一致性逻辑备份）、[MySQL 备份与恢复](https://dev.mysql.com/doc/refman/8.4/en/backup-and-recovery.html)、[OpenSSL enc](https://docs.openssl.org/master/man1/openssl-enc/)。
