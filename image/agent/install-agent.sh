#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Please run this installer as root." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REMOTE_UPGRADE="${ORIN_REMOTE_UPGRADE:-0}"

install -d -m 0755 \
  /opt/juxin-orin/agent \
  /opt/juxin-orin/display \
  /opt/juxin-orin/runtime \
  /usr/lib/juxin-orin \
  /etc/juxin-orin \
  /var/lib/juxin-orin \
  /var/log/juxin-orin

install -m 0755 "${SCRIPT_DIR}/orin_agent.py" /opt/juxin-orin/agent/orin_agent.py
install -m 0644 "${SCRIPT_DIR}/juxin-orin-agent.service" /etc/systemd/system/juxin-orin-agent.service

required_packages=()
python3 -c 'import websocket' >/dev/null 2>&1 || required_packages+=(python3-websocket)
command -v ping >/dev/null 2>&1 || required_packages+=(iputils-ping)
command -v setsid >/dev/null 2>&1 || required_packages+=(util-linux)
if [[ "${#required_packages[@]}" -gt 0 ]]; then
  apt-get update
  DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    "${required_packages[@]}"
fi

if [[ -f "${SCRIPT_DIR}/orin_display.py" && -f "${SCRIPT_DIR}/juxin-orin-display.service" ]]; then
  if ! python3 -c 'from PIL import Image, ImageDraw, ImageFont' >/dev/null 2>&1 \
      || [[ ! -f /usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc ]]; then
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      fonts-noto-cjk python3-pil
  fi
  install -m 0755 "${SCRIPT_DIR}/orin_display.py" /opt/juxin-orin/display/orin_display.py
  if [[ -f "${SCRIPT_DIR}/orin-core.png" ]]; then
    install -m 0644 "${SCRIPT_DIR}/orin-core.png" /opt/juxin-orin/display/orin-core.png
  fi
  install -m 0644 "${SCRIPT_DIR}/juxin-orin-display.service" \
    /etc/systemd/system/juxin-orin-display.service
fi

if [[ -f "${SCRIPT_DIR}/orin-firstboot.sh" ]]; then
  install -m 0755 "${SCRIPT_DIR}/orin-firstboot.sh" /usr/lib/juxin-orin/orin-firstboot.sh
fi
if [[ -f "${SCRIPT_DIR}/juxin-orin-firstboot.service" ]]; then
  install -m 0644 "${SCRIPT_DIR}/juxin-orin-firstboot.service" \
    /etc/systemd/system/juxin-orin-firstboot.service
fi
if [[ -f "${SCRIPT_DIR}/orin-performance.sh" ]]; then
  install -m 0755 "${SCRIPT_DIR}/orin-performance.sh" \
    /usr/lib/juxin-orin/orin-performance.sh
fi
if [[ -f "${SCRIPT_DIR}/juxin-orin-performance.service" ]]; then
  install -m 0644 "${SCRIPT_DIR}/juxin-orin-performance.service" \
    /etc/systemd/system/juxin-orin-performance.service
fi

if [[ ! -f /etc/juxin-orin/image.env ]]; then
  cat >/etc/juxin-orin/image.env <<EOF
ORIN_API_BASE_URL=${ORIN_API_BASE_URL:-https://nvidia.juxinsuanli.cn}
ORIN_AGENT_VERSION=${ORIN_AGENT_VERSION:-0.5.1-orin}
ORIN_IMAGE_VERSION=${ORIN_IMAGE_VERSION:-orin-l4t-36.4.7-v1}
ORIN_HEARTBEAT_INTERVAL=${ORIN_HEARTBEAT_INTERVAL:-60}
ORIN_TASK_POLL_INTERVAL=${ORIN_TASK_POLL_INTERVAL:-60}
ORIN_TASK_TIMEOUT=${ORIN_TASK_TIMEOUT:-240}
ORIN_REQUEST_RETRIES=${ORIN_REQUEST_RETRIES:-2}
EOF
  chmod 0600 /etc/juxin-orin/image.env
fi

for config_file in /etc/juxin-orin/image.env /etc/juxin-orin/agent.env; do
  if [[ -f "$config_file" ]]; then
    sed -i '/^ORIN_IMAGE_LICENSE=/d' "$config_file"
  fi
done

systemctl daemon-reload
services=(juxin-orin-firstboot.service juxin-orin-agent.service)
[[ -f /etc/systemd/system/juxin-orin-performance.service ]] \
  && services+=(juxin-orin-performance.service)
if [[ -f /etc/systemd/system/juxin-orin-display.service ]]; then
  systemctl mask --now getty@tty1.service >/dev/null 2>&1 || true
  services+=(juxin-orin-display.service)
fi
systemctl enable "${services[@]}"

if [[ "$REMOTE_UPGRADE" == "1" ]]; then
  nohup sh -c 'sleep 5; systemctl restart juxin-orin-agent.service; if systemctl cat juxin-orin-display.service >/dev/null 2>&1; then systemctl restart juxin-orin-display.service; fi' \
    >>/opt/juxin-orin/runtime/upgrade.log 2>&1 &
  echo "Agent files installed; service restart scheduled."
else
  systemctl start juxin-orin-firstboot.service
  systemctl restart juxin-orin-agent.service
  if systemctl cat juxin-orin-display.service >/dev/null 2>&1; then
    systemctl restart juxin-orin-display.service
  fi
  systemctl --no-pager --full status juxin-orin-agent.service || true
fi
