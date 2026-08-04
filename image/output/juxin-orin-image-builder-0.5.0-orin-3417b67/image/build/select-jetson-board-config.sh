#!/usr/bin/env bash
set -Eeuo pipefail

BOARD_ID=""
BOARD_SKU=""

die() {
  echo "select-jetson-board-config: $*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --board-id) BOARD_ID="${2:-}"; shift 2 ;;
    --board-sku) BOARD_SKU="${2:-}"; shift 2 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ "$BOARD_ID" =~ ^[0-9]{4}$ ]] || die "invalid board ID: ${BOARD_ID:-missing}"
[[ "$BOARD_SKU" =~ ^[0-9]{4}$ ]] || die "invalid board SKU: ${BOARD_SKU:-missing}"

case "${BOARD_ID}:${BOARD_SKU}" in
  3767:0000|3767:0001)
    echo "jetson-orin-nano-devkit-super-maxn"
    ;;
  3767:0003|3767:0004|3767:0005)
    echo "jetson-orin-nano-devkit-super"
    ;;
  *)
    die "unsupported Jetson module ${BOARD_ID}-${BOARD_SKU}"
    ;;
esac
