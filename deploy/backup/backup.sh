#!/bin/sh
# Encrypted MySQL backup (and optional MinIO volume archive) for the Cangshuo
# Toolbox deployment. Run from deploy/ next to the Compose files.
#
# Usage:
#   BACKUP_PASSPHRASE_FILE=/etc/cangshuo-toolbox/backup.pass \
#   BACKUP_DIR=/var/backups/cangshuo-toolbox sh ./backup/backup.sh
#   BACKUP_PASSPHRASE_FILE=... sh ./backup/backup.sh --ledger-only
#
# Required: BACKUP_PASSPHRASE_FILE (passphrase file, readable only by the operator)
# Optional: COMPOSE_ENV_FILE, COMPOSE_FILES, BACKUP_DIR, BACKUP_RETENTION_DAYS,
#           LEDGER_RETENTION_DAYS, LEDGER_DIR, INCLUDE_MINIO, MINIO_VOLUME,
#           MINIO_HELPER_IMAGE, OPENSSL
set -eu
umask 077

MODE=${1:-full}
case "$MODE" in
  full|--ledger-only) ;;
  *) printf 'usage: backup.sh [--ledger-only]\n' >&2; exit 2 ;;
esac

COMPOSE_ENV_FILE=${COMPOSE_ENV_FILE:-../.env.production}
COMPOSE_FILES=${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.production.yml}
BACKUP_DIR=${BACKUP_DIR:-./backups}
BACKUP_RETENTION_DAYS=${BACKUP_RETENTION_DAYS:-30}
LEDGER_RETENTION_DAYS=${LEDGER_RETENTION_DAYS:-180}
INCLUDE_MINIO=${INCLUDE_MINIO:-false}
MINIO_VOLUME=${MINIO_VOLUME:-${COMPOSE_PROJECT_NAME:-cangshuo-toolbox}_minio-data}
MINIO_HELPER_IMAGE=${MINIO_HELPER_IMAGE:-nginx:1.30.5-alpine3.24}
OPENSSL=${OPENSSL:-openssl}

# Some hosts still ship OpenSSL 1.0.2, which has no -pbkdf2/-iter support.
# Detect once and encrypt every file with the same option set.
if "$OPENSSL" enc -aes-256-cbc -pbkdf2 -iter 1 -md sha256 -salt -pass pass:probe -in /dev/null -out /dev/null 2>/dev/null; then
  ENC_OPTS="-aes-256-cbc -pbkdf2 -iter 600000 -md sha256 -salt"
else
  ENC_OPTS="-aes-256-cbc -salt"
fi

fail() { printf 'backup failed: %s\n' "$1" >&2; exit 1; }

# Intentional word splitting: COMPOSE_FILES holds "-f a -f b".
# shellcheck disable=SC2086
compose() { docker compose --env-file "$COMPOSE_ENV_FILE" $COMPOSE_FILES "$@"; }

[ -n "$BACKUP_DIR" ] && [ "$BACKUP_DIR" != "/" ] || fail "BACKUP_DIR is not a workable path"
[ -n "${BACKUP_PASSPHRASE_FILE:-}" ] || fail "set BACKUP_PASSPHRASE_FILE to a file readable only by the operator"
[ -f "$BACKUP_PASSPHRASE_FILE" ] || fail "the passphrase file does not exist"
command -v docker >/dev/null 2>&1 || fail "docker is required"
command -v "$OPENSSL" >/dev/null 2>&1 || fail "openssl is required"
command -v sha256sum >/dev/null 2>&1 || fail "sha256sum is required"

mkdir -p "$BACKUP_DIR" || fail "cannot create BACKUP_DIR"
BACKUP_DIR=$(cd "$BACKUP_DIR" && pwd)
LEDGER_DIR=${LEDGER_DIR:-$BACKUP_DIR/ledger}
mkdir -p "$LEDGER_DIR" || fail "cannot create LEDGER_DIR"
LEDGER_DIR=$(cd "$LEDGER_DIR" && pwd)

compose ps -q mysql | grep -q . || fail "mysql is not running for $COMPOSE_ENV_FILE"

DATE=$(date -u +%Y%m%dT%H%M%SZ)
WORK_ROOT="$BACKUP_DIR/work/$DATE"
ARCHIVE="$BACKUP_DIR/toolbox-$DATE.tar.gz.enc"
LEDGER_OUT="$LEDGER_DIR/ledger-$DATE.tsv.enc"

cleanup() { rm -rf -- "$WORK_ROOT" "$BACKUP_DIR/work/$DATE.tar.gz"; }
trap cleanup EXIT HUP INT TERM
mkdir -p "$WORK_ROOT"

mysql_query() {
  TMPQ="$WORK_ROOT/.query.$$"
  compose exec -T mysql sh -ec \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u "$MYSQL_USER" "$MYSQL_DATABASE" -e "$1"' sh "$1" \
    > "$TMPQ" || fail "database query failed"
  tr -d '\r' < "$TMPQ"
  rm -f -- "$TMPQ"
}

numeric() {
  case "$1" in
    ''|*[!0-9]*) fail "unexpected non-numeric result from the database: $2" ;;
  esac
}

printf 'exporting deletion ledger\n'
mysql_query "SELECT user_id, DATE_FORMAT(deleted_at,'%Y-%m-%d %H:%i:%s.%f') FROM deleted_account ORDER BY user_id" > "$WORK_ROOT/ledger.tsv"
LEDGER_ROWS=$(wc -l < "$WORK_ROOT/ledger.tsv" | tr -d ' ')
numeric "$LEDGER_ROWS" "ledger line count"
if [ "$LEDGER_ROWS" != "0" ]; then
  awk -F '\t' 'BEGIN{ok=1}
       NF==0{next}
       {
         if (NF!=2 || $1 !~ /^[0-9]+$/ || $2 !~ /^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9]\.[0-9][0-9][0-9][0-9][0-9][0-9]$/) {
           printf "unexpected ledger line %d\n", NR > "/dev/stderr"
           ok=0
           exit 1
         }
       }
       END{if(!ok) exit 1}' "$WORK_ROOT/ledger.tsv" || fail "the deletion ledger export failed validation"
fi
LEDGER_SHA=$(sha256sum "$WORK_ROOT/ledger.tsv" | awk '{print $1}')
# shellcheck disable=SC2086
"$OPENSSL" enc $ENC_OPTS \
  -pass file:"$BACKUP_PASSPHRASE_FILE" -in "$WORK_ROOT/ledger.tsv" -out "$LEDGER_OUT" || fail "ledger encryption failed"
sha256sum "$LEDGER_OUT" | awk '{print $1}' > "$LEDGER_OUT.sha256"
printf 'ledger snapshot: %s (%s rows)\n' "$(basename "$LEDGER_OUT")" "$LEDGER_ROWS"

if [ "$MODE" = full ]; then
  SCHEMA_VERSION=$(mysql_query "SELECT COALESCE(MAX(version),0) FROM flyway_schema_history WHERE success=1")
  numeric "$SCHEMA_VERSION" "schema version"
  TABLE_COUNT=$(mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")
  numeric "$TABLE_COUNT" "table count"

  printf 'dumping mysql database\n'
  compose exec -T mysql sh -ec \
    'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysqldump --single-transaction --routines --triggers --skip-lock-tables --no-tablespaces --default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE"' \
    > "$WORK_ROOT/mysql.sql" || fail "mysqldump failed"
  [ -s "$WORK_ROOT/mysql.sql" ] || fail "mysqldump produced an empty file"
  grep -q 'CREATE TABLE' "$WORK_ROOT/mysql.sql" || fail "dump verification failed"

  MINIO_INCLUDED=false
  if [ "$INCLUDE_MINIO" = true ]; then
    docker volume inspect "$MINIO_VOLUME" >/dev/null 2>&1 || fail "minio volume $MINIO_VOLUME does not exist"
    MINIO_RUNNING=false
    if compose ps --status running --services 2>/dev/null | grep -qx minio; then
      MINIO_RUNNING=true
      compose stop minio >/dev/null
    fi
    docker run --rm --entrypoint sh -v "$MINIO_VOLUME":/data:ro "$MINIO_HELPER_IMAGE" \
      -ec 'tar -czf - -C /data .' > "$WORK_ROOT/minio-data.tar.gz" || fail "minio volume archive failed"
    [ -s "$WORK_ROOT/minio-data.tar.gz" ] || fail "minio archive is empty"
    if [ "$MINIO_RUNNING" = true ]; then compose start minio >/dev/null; fi
    MINIO_INCLUDED=true
    printf 'minio volume archived: %s\n' "$MINIO_VOLUME"
  fi

  MYSQL_SHA=$(sha256sum "$WORK_ROOT/mysql.sql" | awk '{print $1}')
  {
    printf 'created_at_utc=%s\n' "$DATE"
    printf 'schema_version=%s\n' "$SCHEMA_VERSION"
    printf 'table_count=%s\n' "$TABLE_COUNT"
    printf 'ledger_rows=%s\n' "$LEDGER_ROWS"
    printf 'ledger_sha256=%s\n' "$LEDGER_SHA"
    printf 'mysql_dump_sha256=%s\n' "$MYSQL_SHA"
    printf 'minio_included=%s\n' "$MINIO_INCLUDED"
  } > "$WORK_ROOT/manifest.txt"

  TAR_FILES="mysql.sql ledger.tsv manifest.txt"
  if [ "$MINIO_INCLUDED" = true ]; then TAR_FILES="$TAR_FILES minio-data.tar.gz"; fi
  # shellcheck disable=SC2086
  ( cd "$WORK_ROOT" && tar -czf "$BACKUP_DIR/work/$DATE.tar.gz" $TAR_FILES ) || fail "archive creation failed"
# shellcheck disable=SC2086
  "$OPENSSL" enc $ENC_OPTS \
    -pass file:"$BACKUP_PASSPHRASE_FILE" -in "$BACKUP_DIR/work/$DATE.tar.gz" -out "$ARCHIVE" || fail "backup encryption failed"
  sha256sum "$ARCHIVE" | awk '{print $1}' > "$ARCHIVE.sha256"
  printf 'backup archive: %s (%s bytes)\n' "$(basename "$ARCHIVE")" "$(wc -c < "$ARCHIVE" | tr -d ' ')"

  find "$BACKUP_DIR" -maxdepth 1 -type f -name 'toolbox-*.tar.gz.enc' -mtime +"$BACKUP_RETENTION_DAYS" \
    -exec sh -c 'rm -f -- "$1" "$1.sha256"' _ {} \;
fi

find "$LEDGER_DIR" -maxdepth 1 -type f -name 'ledger-*.tsv.enc' -mtime +"$LEDGER_RETENTION_DAYS" \
  -exec sh -c 'rm -f -- "$1" "$1.sha256"' _ {} \;

printf 'backup finished: %s (mode=%s, full retention=%s days, ledger retention=%s days)\n' \
  "$DATE" "$MODE" "$BACKUP_RETENTION_DAYS" "$LEDGER_RETENTION_DAYS"
