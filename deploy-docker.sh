#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

compose() {
  docker compose "$@"
}

require_runtime() {
  command -v docker >/dev/null 2>&1 || {
    echo "Docker is not installed." >&2
    exit 1
  }
  docker compose version >/dev/null 2>&1 || {
    echo "Docker Compose v2 is not available." >&2
    exit 1
  }
}

validate_env() {
  if [[ ! -f .env ]]; then
    echo "Missing .env. Create it from .env.example and set independent secrets." >&2
    exit 1
  fi
  if grep -Eq '(^|=)replace-with-' .env; then
    echo "Refusing to deploy with placeholder secrets in .env." >&2
    exit 1
  fi
  compose config --quiet
}

wait_for_backend() {
  local status=""
  for _ in {1..60}; do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}starting{{end}}' juxin-orin-backend 2>/dev/null || true)"
    if [[ "$status" == "healthy" ]]; then
      echo "Backend is healthy on $(compose port backend 8090)/api/health"
      return 0
    fi
    if [[ "$status" == "unhealthy" ]]; then
      compose logs --tail=100 backend
      return 1
    fi
    sleep 5
  done
  echo "Backend health check timed out." >&2
  compose logs --tail=100 backend
  return 1
}

action="${1:-up}"
require_runtime

case "$action" in
  up)
    validate_env
    compose up -d --build
    wait_for_backend
    compose ps
    ;;
  restart)
    validate_env
    compose up -d --build --force-recreate backend admin
    wait_for_backend
    compose ps
    ;;
  status)
    compose ps
    ;;
  logs)
    compose logs --tail="${2:-200}" -f
    ;;
  down)
    compose down
    ;;
  *)
    echo "Usage: $0 {up|restart|status|logs [lines]|down}" >&2
    exit 2
    ;;
esac
