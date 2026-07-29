#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

compose() {
  docker compose "$@"
}

random_hex() {
  local bytes="$1"
  openssl rand -hex "$bytes"
}

detect_public_host() {
  local host=""
  if command -v curl >/dev/null 2>&1; then
    host="$(curl -4 -fsS --max-time 5 https://api.ipify.org 2>/dev/null || true)"
  fi
  if [[ -z "$host" ]]; then
    host="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  fi
  printf '%s' "${host:-127.0.0.1}"
}

initialize_env() {
  command -v openssl >/dev/null 2>&1 || {
    echo "OpenSSL is required to generate deployment secrets." >&2
    exit 1
  }

  local backend_port="18090"
  local admin_port="18174"
  local public_host
  local public_base_url="https://nvidia.juxinsuanli.cn"
  local admin_password
  public_host="$(detect_public_host)"
  admin_password="$(random_hex 12)"

  umask 077
  {
    printf 'ORIN_BACKEND_PORT=%s\n' "$backend_port"
    printf 'ORIN_ADMIN_PORT=%s\n' "$admin_port"
    printf 'ORIN_DB_URL=jdbc:mysql://mysql:3306/juxin_orin?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false\n'
    printf 'ORIN_DB_USERNAME=juxin_orin\n'
    printf 'ORIN_DB_PASSWORD=%s\n' "$(random_hex 24)"
    printf 'ORIN_MYSQL_ROOT_PASSWORD=%s\n' "$(random_hex 24)"
    printf 'ORIN_CORS_ALLOWED_ORIGINS=%s,http://localhost:5174\n' "$public_base_url"
    printf 'ORIN_PUBLIC_BASE_URL=%s\n' "$public_base_url"
    printf 'ORIN_JWT_SECRET=%s\n' "$(random_hex 32)"
    printf 'ORIN_ADMIN_USERNAME=admin\n'
    printf 'ORIN_ADMIN_PASSWORD=%s\n' "$admin_password"
    printf 'ORIN_WECHAT_APP_ID=%s\n' "${ORIN_WECHAT_APP_ID:-wxd70a6d4437a87a1f}"
    printf 'ORIN_WECHAT_APP_SECRET=%s\n' "${ORIN_WECHAT_APP_SECRET:-}"
    printf 'ORIN_BOSSKG_ENABLED=false\n'
    printf 'ORIN_BOSSKG_API_URL=https://api.example.invalid\n'
    printf 'ORIN_BOSSKG_MER_ID=\n'
    printf 'ORIN_BOSSKG_PROVIDER_ID=\n'
    printf 'ORIN_BOSSKG_TASK_ID=\n'
    printf 'ORIN_BOSSKG_DES_KEY=\n'
    printf 'ORIN_BOSSKG_PRIVATE_KEY=\n'
    printf 'ORIN_BOSSKG_PUBLIC_KEY=\n'
    printf 'ORIN_BOSSKG_CONTRACT_NOTIFY_URL=%s/api/bosskg/notify/contract\n' "$public_base_url"
    printf 'ORIN_BOSSKG_PAYMENT_NOTIFY_URL=%s/api/bosskg/notify/payment\n' "$public_base_url"
  } > .env
  chmod 600 .env

  echo "Generated independent deployment configuration: $ROOT_DIR/.env"
  echo "Detected server address: $public_host"
  echo "Admin URL: $public_base_url"
  echo "Backend health: $public_base_url/api/health"
  echo "Admin username: admin"
  echo "Admin initial password: $admin_password"
  echo "Keep this password secure; it is also stored in .env with mode 600."
}

prepare_env() {
  if [[ ! -f .env ]]; then
    initialize_env
    return
  fi
  if [[ -f .env.example ]] && cmp -s .env .env.example; then
    local backup=".env.placeholder.$(date +%Y%m%d%H%M%S)"
    mv .env "$backup"
    echo "Backed up placeholder configuration to $backup"
    initialize_env
  fi
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
    echo "Missing .env and automatic initialization failed." >&2
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

if [[ "$action" == "init" ]]; then
  prepare_env
  if grep -Eq '(^|=)replace-with-' .env; then
    echo "The existing .env contains placeholders and differs from .env.example; it was not overwritten." >&2
    exit 1
  fi
  echo "Deployment configuration is ready: $ROOT_DIR/.env"
  exit 0
fi

require_runtime

case "$action" in
  up)
    prepare_env
    validate_env
    compose up -d --build
    wait_for_backend
    compose ps
    ;;
  restart)
    prepare_env
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
    echo "Usage: $0 {init|up|restart|status|logs [lines]|down}" >&2
    exit 2
    ;;
esac
