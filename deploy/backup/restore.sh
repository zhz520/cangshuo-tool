#!/bin/sh
# Restores an encrypted backup created by backup.sh and re-applies the
# independent deletion ledger so deleted accounts can never come back.
#
# Usage:
#   BACKUP_PASSPHRASE_FILE=... sh ./backup/restore.sh \
#     --backup /var/backups/cangshuo-toolbox/toolbox-XXXX.tar.gz.enc --confirm
#
# Flags: --backup <file> --confirm [--ledger <file>] [--skip-ledger]
#        [--allow-empty-ledger] [--minio]
set -eu
umask 077

COMPOSE_ENV_FILE=${COMPOSE_ENV_FILE:-../.env.production}
COMPOSE_FILES=${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.production.yml}
LEDGER_DIR=${LEDGER_DIR:-}
MINIO_VOLUME=${MINIO_VOLUME:-${COMPOSE_PROJECT_NAME:-cangshuo-toolbox}_minio-data}
MINIO_HELPER_IMAGE=${MINIO_HELPER_IMAGE:-nginx:1.30.5-alpine3.24}
OPENSSL=${OPENSSL:-openssl}

# Must match backup.sh: OpenSSL 1.0.2 hosts cannot use -pbkdf2/-iter.
if "$OPENSSL" enc -aes-256-cbc -pbkdf2 -iter 1 -md sha256 -salt -pass pass:probe -in /dev/null -out /dev/null 2>/dev/null; then
  DEC_OPTS="-aes-256-cbc -pbkdf2 -iter 600000 -md sha256"
else
  DEC_OPTS="-aes-256-cbc"
fi

fail() { printf 'restore failed: %s\n' "$1" >&2; exit 1; }

# shellcheck disable=SC2086
compose() { docker compose --env-file "$COMPOSE_ENV_FILE" $COMPOSE_FILES "$@"; }

BACKUP_FILE=''; LEDGER_FILE=''; CONFIRM=false
SKIP_LEDGER=false; ALLOW_EMPTY=false; RESTORE_MINIO=false
while [ $# -gt 0 ]; do
  case "$1" in
    --backup) [ $# -ge 2 ] || fail "--backup needs a file"; BACKUP_FILE=$2; shift 2 ;;
    --ledger) [ $# -ge 2 ] || fail "--ledger needs a file"; LEDGER_FILE=$2; shift 2 ;;
    --confirm) CONFIRM=true; shift ;;
    --skip-ledger) SKIP_LEDGER=true; shift ;;
    --allow-empty-ledger) ALLOW_EMPTY=true; shift ;;
    --minio) RESTORE_MINIO=true; shift ;;
    *) fail "unknown argument: $1" ;;
  esac
done

[ "$CONFIRM" = true ] || fail "restore replaces live data; re-run with --confirm"
[ -n "$BACKUP_FILE" ] && [ -f "$BACKUP_FILE" ] || fail "--backup must point to an existing encrypted archive"
[ -n "${BACKUP_PASSPHRASE_FILE:-}" ] && [ -f "$BACKUP_PASSPHRASE_FILE" ] || fail "set BACKUP_PASSPHRASE_FILE to the operator passphrase file"
command -v docker >/dev/null 2>&1 || fail "docker is required"
command -v "$OPENSSL" >/dev/null 2>&1 || fail "openssl is required"
command -v sha256sum >/dev/null 2>&1 || fail "sha256sum is required"

if [ -f "$BACKUP_FILE.sha256" ]; then
  EXPECTED=$(tr -d ' \r\n' < "$BACKUP_FILE.sha256")
  ACTUAL=$(sha256sum "$BACKUP_FILE" | awk '{print $1}')
  [ "$EXPECTED" = "$ACTUAL" ] || fail "archive checksum mismatch"
fi

WORK=$(mktemp -d "${TMPDIR:-/tmp}/toolbox-restore.XXXXXX")
trap 'rm -rf -- "$WORK"' EXIT HUP INT TERM

# shellcheck disable=SC2086
"$OPENSSL" enc -d $DEC_OPTS \
  -pass file:"$BACKUP_PASSPHRASE_FILE" -in "$BACKUP_FILE" -out "$WORK/backup.tar.gz" \
  || fail "decryption failed (wrong passphrase or damaged archive)"
mkdir -p "$WORK/box"
tar -xzf "$WORK/backup.tar.gz" -C "$WORK/box" || fail "archive extraction failed"
[ -s "$WORK/box/mysql.sql" ] || fail "archive does not contain mysql.sql"

LEDGER_ROWS_APPLIED=0
SUPPRESS_SQL="$WORK/merge-ledger.sql"
if [ "$SKIP_LEDGER" = true ]; then
  printf 'ledger merge skipped by explicit --skip-ledger\n'
else
  if [ -z "$LEDGER_FILE" ]; then
    DIR=$LEDGER_DIR
    [ -n "$DIR" ] || DIR=$(dirname "$BACKUP_FILE")/ledger
    [ -d "$DIR" ] || fail "ledger directory $DIR not found; pass --ledger or --skip-ledger"
    LEDGER_FILE=$(ls -1t "$DIR"/ledger-*.tsv.enc "$DIR"/ledger-*.tsv 2>/dev/null | head -n 1 || true)
  fi
  [ -n "$LEDGER_FILE" ] && [ -f "$LEDGER_FILE" ] || fail "no usable deletion ledger found; pass --ledger or --skip-ledger"
  case "$LEDGER_FILE" in
    *.enc) "$OPENSSL" enc -d $DEC_OPTS \
             -pass file:"$BACKUP_PASSPHRASE_FILE" -in "$LEDGER_FILE" -out "$WORK/ledger.tsv" \
             || fail "ledger decryption failed" ;;
    *) cp "$LEDGER_FILE" "$WORK/ledger.tsv" ;;
  esac
  awk -F '\t' 'BEGIN{ok=1}
       NF==0{next}
       {
         if ($1 !~ /^[0-9]+$/ || (NF>=2 && $2 !~ /^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9]\.[0-9][0-9][0-9][0-9][0-9][0-9]$/)) {
           printf "invalid ledger line %d\n", NR > "/dev/stderr"
           ok=0
           exit 1
         }
       }
       END{if(!ok) exit 1}' "$WORK/ledger.tsv" || fail "the deletion ledger failed validation"
  LEDGER_ROWS_APPLIED=$(awk 'NF>0{c++} END{print c+0}' "$WORK/ledger.tsv")
  if [ "$LEDGER_ROWS_APPLIED" = "0" ] && [ "$ALLOW_EMPTY" != true ]; then
    fail "the deletion ledger has no rows; pass --allow-empty-ledger only for a genuinely empty stack"
  fi
  cat > "$WORK/build-ledger.awk" <<'AWK'
BEGIN {
  print "SET FOREIGN_KEY_CHECKS=0;"
  print "CREATE TEMPORARY TABLE restore_ledger (user_id BIGINT PRIMARY KEY, deleted_at DATETIME(3) NULL);"
}
NF > 0 {
  line = sprintf("INSERT IGNORE INTO restore_ledger (user_id, deleted_at) VALUES (%s, ", $1)
  if (NF >= 2) line = line sprintf("'%s'", $2); else line = line "NULL"
  print line ");"
}
END {
  print "DELETE u FROM sys_user u JOIN restore_ledger l ON l.user_id = u.id;"
  print "INSERT IGNORE INTO deleted_account (user_id, deleted_at) SELECT user_id, COALESCE(deleted_at, UTC_TIMESTAMP(3)) FROM restore_ledger;"
  print "SET FOREIGN_KEY_CHECKS=1;"
}
AWK
  awk -F '\t' -f "$WORK/build-ledger.awk" "$WORK/ledger.tsv" > "$SUPPRESS_SQL"
  printf 'deletion ledger selected: %s (%s rows)\n' "$LEDGER_FILE" "$LEDGER_ROWS_APPLIED"
fi

SERVER_ID=$(compose ps -q server | tr -d '\r')
[ -n "$SERVER_ID" ] || fail "server container not found; start the stack before restoring"
compose stop server >/dev/null 2>&1 || true

printf 'importing mysql dump\n'
compose exec -T mysql sh -ec 'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE"' \
  < "$WORK/box/mysql.sql" || fail "mysql import failed"
if [ "$SKIP_LEDGER" != true ]; then
  printf 'applying deletion ledger\n'
  compose exec -T mysql sh -ec 'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE"' \
    < "$SUPPRESS_SQL" || fail "ledger merge failed"
fi

if [ "$RESTORE_MINIO" = true ]; then
  [ -s "$WORK/box/minio-data.tar.gz" ] || fail "--minio was requested but the archive has no minio-data.tar.gz"
  docker volume inspect "$MINIO_VOLUME" >/dev/null 2>&1 || docker volume create "$MINIO_VOLUME" >/dev/null
  MINIO_RUNNING=false
  if compose ps --status running --services 2>/dev/null | grep -qx minio; then
    MINIO_RUNNING=true
    compose stop minio >/dev/null
  fi
  docker run --rm --entrypoint sh -v "$MINIO_VOLUME":/data -i "$MINIO_HELPER_IMAGE" -ec '\
    set -e
    for entry in /data/* /data/.[!.]* /data/..?*; do
      [ -e "$entry" ] || continue
      rm -rf -- "$entry"
    done
    tar -xzf - -C /data' < "$WORK/box/minio-data.tar.gz" || fail "minio volume restore failed"
  if [ "$MINIO_RUNNING" = true ]; then compose start minio >/dev/null; fi
  printf 'minio volume restored: %s\n' "$MINIO_VOLUME"
fi

compose up -d server >/dev/null
printf 'waiting for the API to become healthy\n'
I=0
HEALTHY=false
while [ "$I" -lt 60 ]; do
  SID=$(compose ps -q server | tr -d '\r')
  if [ -n "$SID" ]; then
    STATE=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$SID" 2>/dev/null || echo unknown)
    if [ "$STATE" = healthy ]; then HEALTHY=true; break; fi
    if [ "$STATE" = none ] && [ "$(docker inspect -f '{{.State.Status}}' "$SID" 2>/dev/null || echo unknown)" = running ]; then HEALTHY=true; break; fi
  fi
  sleep 5
  I=$((I+1))
done
[ "$HEALTHY" = true ] || fail "the API did not become healthy after the restore"

if [ -f "$WORK/box/manifest.txt" ]; then
  SCHEMA=$(grep '^schema_version=' "$WORK/box/manifest.txt" | cut -d= -f2 || true)
  printf 'restored schema version: %s\n' "${SCHEMA:-unknown}"
fi
printf 'restore finished: %s (ledger rows applied: %s)\n' "$BACKUP_FILE" "$LEDGER_ROWS_APPLIED"
