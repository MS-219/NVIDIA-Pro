#!/usr/bin/env bash
set -Eeuo pipefail

log() {
  printf 'juxin-orin-performance: %s\n' "$*"
}

if [[ -f /etc/nvfancontrol.conf ]]; then
  sed -i -E 's/^FAN_DEFAULT_PROFILE[[:space:]]+.*/FAN_DEFAULT_PROFILE cool/' \
    /etc/nvfancontrol.conf
  rm -f /var/lib/nvfancontrol/status
  systemctl try-restart nvfancontrol.service || true
  log "selected the cool fan profile"
else
  log "nvfancontrol configuration is unavailable"
fi

log "power mode is managed by the backend through juxin-orin-agent"
