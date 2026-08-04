#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_STYLES="$ROOT_DIR/miniprogram/app.wxss"

button_rule="$(awk '
  /^button[[:space:]]*\{/ { capture=1 }
  capture { print }
  capture && /^}/ { exit }
' "$APP_STYLES")"

for declaration in \
  'box-sizing: border-box' \
  'display: flex' \
  'align-items: center' \
  'justify-content: center' \
  'line-height: normal' \
  'text-align: center'; do
  grep -q "$declaration" <<<"$button_rule"
done

button_count="$(grep -Rho '<button\b' "$ROOT_DIR/miniprogram/pages" --include='*.wxml' | wc -l | tr -d ' ')"
(( button_count > 0 ))

echo "mini-program button alignment test: PASS (${button_count} buttons covered)"
