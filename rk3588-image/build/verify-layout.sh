#!/usr/bin/env bash
set -Eeuo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

test -f "$root_dir/README.md"
test -f "$root_dir/build/fetch-public-assets.sh"
test -f "$root_dir/device/README.md"

if rg -n --glob '*.sh' --glob '!verify-layout.sh' \
  'l4t_initrd_flash|nvpmodel|tegrastats|Jetson_Linux' "$root_dir/build" >/dev/null; then
  echo 'RK 工程不得依赖 Jetson/L4T 工具链' >&2
  exit 1
fi

printf 'RK3588S 镜像工程目录结构正常：%s\n' "$root_dir"
