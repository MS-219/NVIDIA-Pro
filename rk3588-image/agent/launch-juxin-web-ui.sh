#!/bin/sh
set -eu

WEB_ROOT=/opt/juxin-rk3588/web
STATE_DIR=/var/lib/juxin-rk3588
mkdir -p "$WEB_ROOT" "$STATE_DIR" /var/log
ln -sfn "$STATE_DIR/display-status.json" "$WEB_ROOT/status.json"

# Serve the local dashboard so Chromium can poll Agent telemetry without
# exposing the state file through a file:// URL.
/usr/bin/python3 -m http.server 8765 --bind 127.0.0.1 --directory "$WEB_ROOT" \
  >>/var/log/juxin-rk3588-web.log 2>&1 &

sleep 1
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/0}"
export WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-0}"
mkdir -p "$XDG_RUNTIME_DIR"

exec /usr/bin/chromium \
  --no-sandbox --disable-gpu-sandbox --ozone-platform=wayland \
  --kiosk --start-maximized --no-first-run --disable-session-crashed-bubble \
  --user-data-dir="$STATE_DIR/chromium" \
  http://127.0.0.1:8765/index.html
