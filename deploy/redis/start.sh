#!/bin/sh
set -eu
case "${REDIS_PASSWORD:-}" in
    ''|*[!0-9a-fA-F]*)
        echo 'REDIS_PASSWORD must be a non-empty hexadecimal value.' >&2
        exit 1
        ;;
esac
umask 077
redis_config=$(mktemp /tmp/toolbox-redis.XXXXXX)
printf '%s\n' 'bind 0.0.0.0' 'protected-mode yes' 'port 6379' 'dir /data' \
    'appendonly yes' 'appendfsync everysec' "requirepass $REDIS_PASSWORD" > "$redis_config"
exec redis-server "$redis_config"
