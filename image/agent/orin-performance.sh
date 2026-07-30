#!/usr/bin/env bash
set -Eeuo pipefail

log() {
  printf 'juxin-orin-performance: %s\n' "$*"
}

if [[ -x /usr/sbin/nvpmodel ]]; then
  if timeout 20 /usr/sbin/nvpmodel -m 2 </dev/null; then
    log "selected MAXN_SUPER power mode (mode 2)"
  else
    log "unable to select MAXN_SUPER; verify the super board configuration"
  fi
else
  log "nvpmodel is unavailable"
fi

if [[ -f /etc/nvfancontrol.conf ]]; then
  sed -i -E 's/^FAN_DEFAULT_PROFILE[[:space:]]+.*/FAN_DEFAULT_PROFILE cool/' \
    /etc/nvfancontrol.conf
  rm -f /var/lib/nvfancontrol/status
  systemctl try-restart nvfancontrol.service || true
  log "selected the cool fan profile"
else
  log "nvfancontrol configuration is unavailable"
fi
