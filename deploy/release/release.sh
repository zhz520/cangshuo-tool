#!/bin/sh
# Staged release for the Cangshuo Toolbox deployment.
#
# Builds version-tagged server/admin images, verifies them on a staging
# Compose project, promotes the same images to production, then runs release
# smoke checks. Rollback switches production back to the previously recorded
# release tag. Never prints secrets.
#
# Usage (from deploy/):
#   sh ./release/release.sh preflight
#   sh ./release/release.sh build --tag 0.2.0
#   sh ./release/release.sh stage --tag 0.2.0 [--prune-images]
#   sh ./release/release.sh rollback
#   sh ./release/release.sh status
#
# Required: PROD_PROJECT
# Optional: STAGING_PROJECT (default "$PROD_PROJECT-staging"), PROD_ENV_FILE,
#   STAGING_ENV_FILE, PROD_COMPOSE_FILES, STAGING_COMPOSE_FILES, RELEASE_REPO,
#   STATE_FILE, PYTHON, STAGING_API_URL, STAGING_WEB_URL, PROD_API_URL,
#   PROD_WEB_URL, CORS_ORIGIN, KEEP_TAGS
set -eu
umask 077

fail() { printf 'release failed: %s\n' "$1" >&2; exit 1; }

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
SMOKE_SCRIPT="$SCRIPT_DIR/../../scripts/smoke_release.py"

CMD=${1:-}
[ $# -gt 0 ] && shift
TAG=''
PRUNE=false
while [ $# -gt 0 ]; do
  case "$1" in
    --tag) [ $# -ge 2 ] || fail "--tag needs a value"; TAG=$2; shift 2 ;;
    --prune-images) PRUNE=true; shift ;;
    *) fail "unknown argument: $1" ;;
  esac
done

PROD_PROJECT=${PROD_PROJECT:-}
STAGING_PROJECT=${STAGING_PROJECT:-${PROD_PROJECT}-staging}
PROD_ENV_FILE=${PROD_ENV_FILE:-../.env.production}
STAGING_ENV_FILE=${STAGING_ENV_FILE:-../.env.staging}
PROD_COMPOSE_FILES=${PROD_COMPOSE_FILES:--f docker-compose.yml -f docker-compose.production.yml}
STAGING_COMPOSE_FILES=${STAGING_COMPOSE_FILES:--f docker-compose.yml -f docker-compose.local.yml}
RELEASE_REPO=${RELEASE_REPO:-cangshuo-toolbox-release}
STATE_FILE=${STATE_FILE:-$SCRIPT_DIR/../release-state/state.env}
PYTHON=${PYTHON:-python3}
KEEP_TAGS=${KEEP_TAGS:-3}

file_value() { if [ -f "$1" ]; then sed -n "s/^$2=//p" "$1" | tr -d '\r' | tail -n 1; fi; }

if [ -z "${PROD_API_URL:-}" ]; then
  LOCAL_PORT=$(file_value "$PROD_ENV_FILE" LOCAL_API_PORT)
  DOMAIN=$(file_value "$PROD_ENV_FILE" API_DOMAIN)
  if [ -n "$LOCAL_PORT" ]; then PROD_API_URL="http://127.0.0.1:$LOCAL_PORT/api/v1"
  elif [ -n "$DOMAIN" ]; then PROD_API_URL="https://$DOMAIN/api/v1"; fi
fi
if [ -z "${PROD_WEB_URL:-}" ]; then
  LOCAL_PORT=$(file_value "$PROD_ENV_FILE" LOCAL_WEB_PORT)
  DOMAIN=$(file_value "$PROD_ENV_FILE" WEB_DOMAIN)
  if [ -n "$LOCAL_PORT" ]; then PROD_WEB_URL="http://127.0.0.1:$LOCAL_PORT"
  elif [ -n "$DOMAIN" ]; then PROD_WEB_URL="https://$DOMAIN"; fi
fi
if [ -z "${STAGING_API_URL:-}" ]; then
  LOCAL_PORT=$(file_value "$STAGING_ENV_FILE" LOCAL_API_PORT)
  [ -n "$LOCAL_PORT" ] && STAGING_API_URL="http://127.0.0.1:$LOCAL_PORT/api/v1" || STAGING_API_URL=''
fi
if [ -z "${STAGING_WEB_URL:-}" ]; then
  LOCAL_PORT=$(file_value "$STAGING_ENV_FILE" LOCAL_WEB_PORT)
  [ -n "$LOCAL_PORT" ] && STAGING_WEB_URL="http://127.0.0.1:$LOCAL_PORT" || STAGING_WEB_URL=''
fi
CORS_ORIGIN=${CORS_ORIGIN:-$(file_value "$PROD_ENV_FILE" CORS_ALLOWED_ORIGINS | cut -d, -f1)}

# Intentional word splitting: the *_COMPOSE_FILES variables hold "-f a -f b".
# shellcheck disable=SC2086
prod_compose() { docker compose -p "$PROD_PROJECT" --env-file "$PROD_ENV_FILE" $PROD_COMPOSE_FILES "$@"; }
# shellcheck disable=SC2086
staging_compose() { docker compose -p "$STAGING_PROJECT" --env-file "$STAGING_ENV_FILE" $STAGING_COMPOSE_FILES "$@"; }

state_get() { if [ -f "$STATE_FILE" ]; then sed -n "s/^$1=//p" "$STATE_FILE" | tr -d '\r' | tail -n 1; fi; }

write_state() { # current previous
  mkdir -p "$(dirname "$STATE_FILE")"
  TMP_STATE="$STATE_FILE.tmp.$$"
  {
    printf 'CURRENT_TAG=%s\n' "$1"
    printf 'PREVIOUS_TAG=%s\n' "$2"
    printf 'RELEASED_AT=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'SERVER_IMAGE=%s-api:%s\n' "$RELEASE_REPO" "$1"
    printf 'WEB_IMAGE=%s-web:%s\n' "$RELEASE_REPO" "$1"
  } > "$TMP_STATE"
  mv "$TMP_STATE" "$STATE_FILE"
}

require_tag() {
  case "${1:-}" in
    ''|*[!A-Za-z0-9._-]*) fail "--tag must be a simple version such as 0.2.0" ;;
  esac
}

require_images() { # tag
  docker image inspect "$RELEASE_REPO-api:$1" >/dev/null 2>&1 || fail "missing image $RELEASE_REPO-api:$1 (run build first)"
  docker image inspect "$RELEASE_REPO-web:$1" >/dev/null 2>&1 || fail "missing image $RELEASE_REPO-web:$1 (run build first)"
}

smoke() { # api_url web_url label
  [ -n "$1" ] || fail "no API URL configured for the $3 smoke test"
  SMOKE_ARGS="--base-url $1"
  [ -n "$2" ] && SMOKE_ARGS="$SMOKE_ARGS --web-url $2"
  [ -n "$CORS_ORIGIN" ] && SMOKE_ARGS="$SMOKE_ARGS --origin $CORS_ORIGIN"
  # shellcheck disable=SC2086
  $PYTHON "$SMOKE_SCRIPT" $SMOKE_ARGS || fail "$3 smoke checks failed"
}

preflight() {
  [ -n "$PROD_PROJECT" ] || fail "set PROD_PROJECT to the production Compose project name"
  [ "$PROD_PROJECT" != "$STAGING_PROJECT" ] || fail "staging and production must use different project names"
  command -v docker >/dev/null 2>&1 || fail "docker is required"
  command -v "$PYTHON" >/dev/null 2>&1 || fail "python ($PYTHON) is required for smoke checks"
  [ -f "$SMOKE_SCRIPT" ] || fail "smoke script not found at $SMOKE_SCRIPT"
  [ -f "$PROD_ENV_FILE" ] || fail "production env file $PROD_ENV_FILE not found"
  [ -f "$STAGING_ENV_FILE" ] || fail "staging env file $STAGING_ENV_FILE not found"
  prod_compose config --quiet || fail "production Compose configuration is invalid"
  staging_compose config --quiet || fail "staging Compose configuration is invalid"
  FREE_KB=$(df -Pk "$SCRIPT_DIR" | awk 'NR==2{print $4}')
  case "$FREE_KB" in ''|*[!0-9]*) fail "cannot determine free disk space" ;; esac
  [ "$FREE_KB" -ge 2097152 ] || fail "less than 2 GiB free disk space"
  case "$PROD_ENV_FILE" in
    *.env.production)
      WEB_DOMAIN=$(file_value "$PROD_ENV_FILE" WEB_DOMAIN)
      API_DOMAIN=$(file_value "$PROD_ENV_FILE" API_DOMAIN)
      [ -n "$WEB_DOMAIN" ] && [ -n "$API_DOMAIN" ] || fail "production env needs WEB_DOMAIN and API_DOMAIN"
      for domain in "$WEB_DOMAIN" "$API_DOMAIN"; do
        [ -s "$SCRIPT_DIR/../certs/$domain/fullchain.pem" ] || fail "missing certificate for $domain"
        [ -s "$SCRIPT_DIR/../certs/$domain/privkey.pem" ] || fail "missing private key for $domain"
      done
      ;;
  esac
  printf 'preflight ok: production=%s staging=%s api=%s\n' "$PROD_PROJECT" "$STAGING_PROJECT" "${PROD_API_URL:-unset}"
}

build() {
  require_tag "$TAG"
  preflight
  printf 'building %s-api:%s and %s-web:%s\n' "$RELEASE_REPO" "$TAG" "$RELEASE_REPO" "$TAG"
  SERVER_IMAGE="$RELEASE_REPO-api:$TAG" WEB_IMAGE="$RELEASE_REPO-web:$TAG" \
    prod_compose build server admin
  require_images "$TAG"
  printf 'build ok: %s-api:%s\n' "$RELEASE_REPO" "$TAG"
}

stage() {
  require_tag "$TAG"
  preflight
  require_images "$TAG"
  OLD_TAG=$(state_get CURRENT_TAG)
  PREVIOUS_TAG=$(state_get PREVIOUS_TAG)
  [ "$OLD_TAG" != "$TAG" ] || fail "tag $TAG is already the recorded current release"

  printf 'starting staging project %s with tag %s\n' "$STAGING_PROJECT" "$TAG"
  if ! SERVER_IMAGE="$RELEASE_REPO-api:$TAG" WEB_IMAGE="$RELEASE_REPO-web:$TAG" \
       staging_compose up -d --wait --wait-timeout 300; then
    staging_compose stop >/dev/null 2>&1 || true
    fail "staging project did not become healthy (volumes kept for debugging)"
  fi
  if ! smoke "$STAGING_API_URL" "$STAGING_WEB_URL" staging; then
    staging_compose stop >/dev/null 2>&1 || true
    exit 1
  fi

  printf 'promoting tag %s to production project %s\n' "$TAG" "$PROD_PROJECT"
  SERVER_IMAGE="$RELEASE_REPO-api:$TAG" WEB_IMAGE="$RELEASE_REPO-web:$TAG" \
    prod_compose up -d --wait --wait-timeout 300 || fail "production update did not become healthy"
  if ! smoke "$PROD_API_URL" "$PROD_WEB_URL" production; then
    printf 'production smoke failed; attempting automatic rollback\n' >&2
    if [ -n "$OLD_TAG" ]; then
      require_images "$OLD_TAG" && SERVER_IMAGE="$RELEASE_REPO-api:$OLD_TAG" WEB_IMAGE="$RELEASE_REPO-web:$OLD_TAG" \
        prod_compose up -d --wait --wait-timeout 300 && write_state "$OLD_TAG" "$PREVIOUS_TAG" || true
    fi
    exit 1
  fi

  write_state "$TAG" "$OLD_TAG"
  printf 'stage ok: current=%s previous=%s\n' "$TAG" "${OLD_TAG:-none}"

  if [ "$PRUNE" = true ]; then
    prune_images "$TAG" "$OLD_TAG"
  fi
}

prune_images() { # current previous
  INDEX=0
  docker images --format '{{.Repository}}:{{.Tag}}|{{.CreatedAt}}' | grep "^$RELEASE_REPO-api:" | sort -t'|' -k2 -r | cut -d'|' -f1 > "${TMPDIR:-/tmp}/release-tags.$$" || true
  while IFS= read -r IMAGE; do
    INDEX=$((INDEX+1))
    TAG_NAME=${IMAGE##*:}
    if [ "$INDEX" -le "$KEEP_TAGS" ] || [ "$TAG_NAME" = "$1" ] || [ "$TAG_NAME" = "$2" ]; then continue; fi
    docker rmi "$RELEASE_REPO-api:$TAG_NAME" >/dev/null 2>&1 || true
    docker rmi "$RELEASE_REPO-web:$TAG_NAME" >/dev/null 2>&1 || true
  done < "${TMPDIR:-/tmp}/release-tags.$$"
  rm -f -- "${TMPDIR:-/tmp}/release-tags.$$"
}

rollback() {
  preflight
  CURRENT=$(state_get CURRENT_TAG)
  TARGET=$(state_get PREVIOUS_TAG)
  [ -n "$TARGET" ] || fail "no previous release recorded in $STATE_FILE"
  require_images "$TARGET"
  printf 'rolling production %s back to %s\n' "$PROD_PROJECT" "$TARGET"
  SERVER_IMAGE="$RELEASE_REPO-api:$TARGET" WEB_IMAGE="$RELEASE_REPO-web:$TARGET" \
    prod_compose up -d --wait --wait-timeout 300 || fail "rollback update did not become healthy"
  smoke "$PROD_API_URL" "$PROD_WEB_URL" production
  write_state "$TARGET" "$CURRENT"
  printf 'rollback ok: current=%s previous=%s\n' "$TARGET" "${CURRENT:-none}"
}

status() {
  if [ -f "$STATE_FILE" ]; then cat "$STATE_FILE"; else printf 'no release state at %s\n' "$STATE_FILE"; fi
  docker ps --filter "label=com.docker.compose.project=$PROD_PROJECT" \
    --format '{{.Label "com.docker.compose.service"}}|{{.Image}}|{{.Status}}' 2>/dev/null || true
}

case "$CMD" in
  preflight) preflight ;;
  build) build ;;
  stage) stage ;;
  rollback) rollback ;;
  status) status ;;
  *) fail "usage: release.sh preflight|build|stage|rollback|status [--tag TAG] [--prune-images]" ;;
esac
