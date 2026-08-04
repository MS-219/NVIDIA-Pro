#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="${1:-https://nvidia.juxinsuanli.cn}"
IMAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/juxin-orin-readiness.XXXXXX")"

cleanup() {
  rm -rf -- "$TEMP_DIR"
}
trap cleanup EXIT

if [[ ! "$BASE_URL" =~ ^https://[A-Za-z0-9.-]+(:[0-9]+)?$ ]]; then
  echo "backend readiness: base URL must be an HTTPS origin" >&2
  exit 2
fi

if ! curl -fsS --proto '=https' --tlsv1.2 --connect-timeout 8 --max-time 20 \
  "$BASE_URL/api/health" >"$TEMP_DIR/health.json"; then
  echo "backend readiness: health endpoint is unavailable" >&2
  exit 1
fi
if ! curl -fsS --proto '=https' --tlsv1.2 --connect-timeout 8 --max-time 20 \
  "$BASE_URL/api/edge/capabilities" >"$TEMP_DIR/capabilities.json"; then
  echo "backend readiness: edge protocol 2 is not deployed" >&2
  exit 1
fi

python3 - "$TEMP_DIR/health.json" "$TEMP_DIR/capabilities.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    health = json.load(handle)
if health.get("status") != "UP" or health.get("database") != "UP":
    raise SystemExit("backend readiness: application or database is not UP")

with open(sys.argv[2], encoding="utf-8") as handle:
    capabilities = json.load(handle)
data = capabilities.get("data") or {}
required = {
    "protocolVersion": "2",
    "minimumAgentVersion": "0.5.0-orin",
    "directEnrollment": True,
    "imageLicenseRequired": False,
    "deviceTokenAuthentication": True,
    "fullscreenStatusDisplay": True,
    "atomicTaskClaim": True,
    "persistentResultOutbox": True,
    "authenticatedRemoteTerminal": True,
}
for key, expected in required.items():
    if data.get(key) != expected:
        raise SystemExit(f"backend readiness: capability {key} is not {expected!r}")
PY

for asset in \
  orin_agent.py \
  orin_display.py \
  orin-core.png \
  install-agent.sh \
  juxin-orin-agent.service \
  juxin-orin-display.service; do
  curl -fsS --proto '=https' --tlsv1.2 --connect-timeout 8 --max-time 20 \
    "$BASE_URL/api/agent/$asset" >"$TEMP_DIR/$asset"
  if ! cmp -s "$IMAGE_DIR/agent/$asset" "$TEMP_DIR/$asset"; then
    echo "backend readiness: deployed $asset differs from the image source" >&2
    exit 1
  fi
done

echo "backend readiness: PASS ($BASE_URL, protocol 2, agent 0.5.0-orin)"
