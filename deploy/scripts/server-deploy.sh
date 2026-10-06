#!/usr/bin/env bash
# 沧烁工具箱：宝塔模式一键部署（在服务器 root 下运行，可重复执行）
# 用法（宝塔面板 → 终端）：
#   curl -fsSL https://cdn.jsdelivr.net/gh/zhz520/cangshuo-tool@main/deploy/scripts/server-deploy.sh -o /root/deploy-toolbox.sh
#   nohup bash /root/deploy-toolbox.sh > /root/toolbox-deploy.log 2>&1 &
#   tail -f /root/toolbox-deploy.log
# 说明：只安装/启动本项目，不删除任何数据；重复执行会跳过已完成步骤。
set -euo pipefail

TARGET=/www/wwwroot/cangshuo-toolbox
REPO_GITHUB=https://github.com/zhz520/cangshuo-tool.git
REPO_MIRROR=https://gitclone.com/github.com/zhz520/cangshuo-tool.git
REPO_TARBALL=https://ghproxy.net/https://github.com/zhz520/cangshuo-tool/archive/refs/heads/main.tar.gz
SUMMARY=/root/toolbox-deploy-summary.txt
ADMIN_PW_FILE=/root/toolbox-admin-password.txt
BACKUP_DIR=/www/backup/cangshuo-toolbox
BACKUP_PASS=/root/toolbox-backup.pass
COMPOSE="docker compose --env-file ../.env.production -f docker-compose.yml -f docker-compose.baota.yml"

log() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
fail() { log "错误：$*"; log "部署中止，请把 /root/toolbox-deploy.log 的内容发给我。"; exit 1; }

[ "$(id -u)" = 0 ] || fail "请用 root 用户运行（宝塔面板终端默认就是 root）"

log "==== 1/9 系统信息 ===="
if [ -f /etc/os-release ]; then . /etc/os-release; fi
log "系统：${PRETTY_NAME:-未知}  内核：$(uname -r)"
log "CPU：$(nproc) 核   内存：$(free -h | awk '/^Mem:/{print $2}')   磁盘可用：$(df -h / | awk 'NR==2{print $4}')"

log "==== 2/9 基础工具 ===="
NEED=""
for c in git curl openssl; do command -v "$c" >/dev/null 2>&1 || NEED="$NEED $c"; done
if [ -n "$NEED" ]; then
  log "缺少$NEED，开始安装..."
  if command -v dnf >/dev/null 2>&1; then dnf install -y $NEED
  elif command -v yum >/dev/null 2>&1; then yum install -y $NEED
  elif command -v apt-get >/dev/null 2>&1; then apt-get update && apt-get install -y $NEED
  else fail "无法识别包管理器，请手动安装 git/curl/openssl"; fi
fi
log "基础工具就绪"

log "==== 3/9 Docker 与 Compose ===="
if ! command -v docker >/dev/null 2>&1; then
  log "未检测到 Docker，尝试在线安装（阿里云镜像）..."
  if curl -fsSL https://get.docker.com -o /tmp/get-docker.sh; then
    sh /tmp/get-docker.sh --mirror Aliyun || true
  fi
fi
command -v docker >/dev/null 2>&1 || fail "Docker 未安装成功：请在宝塔面板「软件商店 → Docker 管理器」安装 Docker 后重新运行本脚本"
systemctl enable --now docker >/dev/null 2>&1 || true
docker compose version >/dev/null 2>&1 || fail "缺少 docker compose 插件：请在宝塔 Docker 管理器中补装 Compose，或执行 dnf install -y docker-compose-plugin"
log "$(docker --version)"
log "$(docker compose version | sed -n '1p')"

log "==== 4/9 Docker 镜像源检查 ===="
if docker pull hello-world >/dev/null 2>&1; then
  log "Docker Hub 直连可用"
  docker rmi hello-world >/dev/null 2>&1 || true
else
  log "直连失败，写入国内镜像加速并重启 Docker..."
  [ -f /etc/docker/daemon.json ] && cp -a /etc/docker/daemon.json "/etc/docker/daemon.json.bak.$(date +%s)"
  mkdir -p /etc/docker
  cat > /etc/docker/daemon.json <<'JSON'
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run",
    "https://hub.rat.dev",
    "https://docker.xuanyuan.me"
  ]
}
JSON
  systemctl restart docker
  sleep 5
  docker pull hello-world >/dev/null 2>&1 || fail "镜像源仍不可用，请把本段日志发我"
  docker rmi hello-world >/dev/null 2>&1 || true
  log "镜像加速已生效"
fi

log "==== 5/9 拉取代码 ===="
if [ -d "$TARGET/.git" ]; then
  log "已存在代码目录，执行 git pull"
  git -C "$TARGET" pull --ff-only || log "git pull 失败，继续使用现有代码"
else
  mkdir -p "$(dirname "$TARGET")"
  if ! git clone --depth 1 "$REPO_GITHUB" "$TARGET" 2>/dev/null; then
    log "GitHub 直连失败，尝试镜像..."
    rm -rf "$TARGET"
    if ! git clone --depth 1 "$REPO_MIRROR" "$TARGET" 2>/dev/null; then
      log "镜像失败，尝试压缩包..."
      rm -rf "$TARGET"; mkdir -p "$TARGET"
      curl -fsSL "$REPO_TARBALL" -o /tmp/toolbox.tar.gz || fail "代码下载失败，请检查服务器网络"
      tar -xzf /tmp/toolbox.tar.gz -C "$TARGET" --strip-components=1
    fi
  fi
fi
[ -f "$TARGET/deploy/docker-compose.yml" ] || fail "代码不完整，请把日志发我"
log "代码就绪：$TARGET"

log "==== 6/9 生成配置 ===="
cd "$TARGET"
if [ ! -f .env.production ]; then
  sh deploy/scripts/initialize-env.sh .env.production || fail "生成 .env.production 失败"
  ADMIN_PW=$(openssl rand -hex 10)
  sed -i "s|^ADMIN_BOOTSTRAP_USERNAME=.*|ADMIN_BOOTSTRAP_USERNAME=admin|" .env.production
  sed -i "s|^ADMIN_BOOTSTRAP_PASSWORD=.*|ADMIN_BOOTSTRAP_PASSWORD=${ADMIN_PW}|" .env.production
  MEM_MB=$(free -m | awk '/^Mem:/{print $2}')
  if [ "$MEM_MB" -lt 3584 ]; then
    sed -i '/^JAVA_TOOL_OPTIONS=/d' .env.production
    printf 'JAVA_TOOL_OPTIONS=-Xmx384m -Dfile.encoding=UTF-8\n' >> .env.production
    log "检测到 ${MEM_MB}MB 内存，已把 JVM 堆限制为 384MB"
  fi
  chmod 600 .env.production
  printf '%s\n' "$ADMIN_PW" > "$ADMIN_PW_FILE"
  chmod 600 "$ADMIN_PW_FILE"
  log "已生成 .env.production（后台账号 admin，密码存于 $ADMIN_PW_FILE）"
else
  log ".env.production 已存在，保持不变"
fi

log "==== 7/9 构建镜像（首次约 10-25 分钟，请耐心等待）===="
cd "$TARGET/deploy"
$COMPOSE build server admin

log "==== 8/9 启动并自检 ===="
$COMPOSE up -d --wait --wait-timeout 300
sleep 3
$COMPOSE ps
HEALTH=$(curl -sS -m 10 -H 'Host: toolapi.zhzgo.cn' http://127.0.0.1:18080/api/v1/health || true)
log "健康检查：$HEALTH"

log "==== 9/9 备份任务（每日全量 + 每 15 分钟台账）===="
mkdir -p "$BACKUP_DIR"
if [ ! -f "$BACKUP_PASS" ]; then openssl rand -hex 32 > "$BACKUP_PASS"; chmod 600 "$BACKUP_PASS"; log "已生成备份口令 $BACKUP_PASS"; fi
if ! crontab -l 2>/dev/null | grep -q 'toolbox-backup-full'; then
  {
    crontab -l 2>/dev/null || true
    echo "17 2 * * * cd $TARGET/deploy && COMPOSE_ENV_FILE=../.env.production COMPOSE_FILES='-f docker-compose.yml -f docker-compose.baota.yml' BACKUP_DIR=$BACKUP_DIR BACKUP_PASSPHRASE_FILE=$BACKUP_PASS sh ./backup/backup.sh >> /www/backup/toolbox-backup.log 2>&1 # toolbox-backup-full"
    echo "*/15 * * * * cd $TARGET/deploy && COMPOSE_ENV_FILE=../.env.production COMPOSE_FILES='-f docker-compose.yml -f docker-compose.baota.yml' BACKUP_DIR=$BACKUP_DIR BACKUP_PASSPHRASE_FILE=$BACKUP_PASS sh ./backup/backup.sh --ledger-only >> /www/backup/toolbox-backup.log 2>&1 # toolbox-backup-ledger"
  } | crontab -
  log "已写入两条备份计划任务"
else
  log "备份任务已存在，跳过"
fi

{
  echo "沧烁工具箱 服务端部署结果"
  echo "完成时间：$(date '+%F %T')"
  echo "健康检查：$HEALTH"
  echo
  echo "后台地址：https://tool.zhzgo.cn/admin/  账号：admin"
  if [ -f "$ADMIN_PW_FILE" ]; then echo "管理员密码：$(cat "$ADMIN_PW_FILE")"; else echo "管理员密码：沿用已有 .env.production"; fi
  echo
  echo "下一步（宝塔面板）："
  echo "1) 网站 → 添加站点 tool.zhzgo.cn（纯静态、不建数据库）"
  echo "2) 该站点 → 反向代理 → 目标 http://127.0.0.1:18080，发送域名填 \$host，关闭缓存"
  echo "3) 该站点 → SSL → 申请 Let's Encrypt 证书 → 开启强制 HTTPS"
  echo "4) 对 toolapi.zhzgo.cn 重复第 1-3 步"
  echo "5) 全部完成后执行： curl -sS https://toolapi.zhzgo.cn/api/v1/health"
  echo
  echo "常用命令（在 $TARGET/deploy 目录下）："
  echo "  $COMPOSE ps"
  echo "  $COMPOSE logs -f --tail 100 server"
} > "$SUMMARY"

echo
log "==== 部署完成 ===="
cat "$SUMMARY"
log "摘要已保存到 $SUMMARY"
