#!/bin/sh
set -eu
environment_name=${1:-.env}
case "$environment_name" in
    .env|.env.production) ;;
    *) echo 'Use .env or .env.production.' >&2; exit 1 ;;
esac
command -v openssl >/dev/null 2>&1 || { echo 'OpenSSL is required to generate credentials.' >&2; exit 1; }
repository_directory=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
environment_path=$repository_directory/$environment_name
if [ -e "$environment_path" ]; then
    echo "$environment_name already exists. Preserve its credentials and edit it directly if needed." >&2
    exit 1
fi
umask 077
set -C
{
    while IFS= read -r environment_line || [ -n "$environment_line" ]; do
        case "$environment_line" in
            MYSQL_ROOT_PASSWORD=*|MYSQL_PASSWORD=*|REDIS_PASSWORD=*|MINIO_ROOT_PASSWORD=*)
                secret_name=${environment_line%%=*}
                deployment_secret=$(openssl rand -hex 32)
                printf '%s=%s\n' "$secret_name" "$deployment_secret"
                ;;
            COMPOSE_PROJECT_NAME=*)
                if [ "$environment_name" = '.env.production' ]; then
                    printf '%s\n' 'COMPOSE_PROJECT_NAME=cangshuo-toolbox-production'
                else
                    printf '%s\n' "$environment_line"
                fi
                ;;
            *) printf '%s\n' "$environment_line" ;;
        esac
    done < "$repository_directory/.env.example"
} > "$environment_path"
echo "Created $environment_name with random credentials. Keep this file private."
