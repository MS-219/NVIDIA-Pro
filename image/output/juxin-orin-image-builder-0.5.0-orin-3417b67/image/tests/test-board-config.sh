#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SELECTOR="$IMAGE_DIR/build/select-jetson-board-config.sh"

for sku in 0000 0001; do
  [[ "$("$SELECTOR" --board-id 3767 --board-sku "$sku")" \
    == "jetson-orin-nano-devkit-super-maxn" ]]
done

for sku in 0003 0004 0005; do
  [[ "$("$SELECTOR" --board-id 3767 --board-sku "$sku")" \
    == "jetson-orin-nano-devkit-super" ]]
done

if "$SELECTOR" --board-id 3767 --board-sku 9999 >/dev/null 2>&1; then
  echo "unsupported Jetson SKU was accepted" >&2
  exit 1
fi

echo "Jetson board config test: PASS"
