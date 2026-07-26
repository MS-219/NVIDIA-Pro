#!/usr/bin/env bash
set -euo pipefail

install -d -m 0755 /opt/juxin-orin/agent /etc/juxin-orin /var/lib/juxin-orin /var/log/juxin-orin
install -m 0755 "$(dirname "$0")/orin_agent.py" /opt/juxin-orin/agent/orin_agent.py
install -m 0644 "$(dirname "$0")/juxin-orin-agent.service" /etc/systemd/system/juxin-orin-agent.service

if [[ ! -f /etc/juxin-orin/agent.env ]]; then
  install -m 0600 "$(dirname "$0")/agent.env.example" /etc/juxin-orin/agent.env
  echo "请先编辑 /etc/juxin-orin/agent.env，再启动 juxin-orin-agent.service"
  exit 0
fi

systemctl daemon-reload
systemctl enable --now juxin-orin-agent.service
