#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP_ROOT="$(mktemp -d)"
TEMP_ROOT_2="$(mktemp -d)"
TEMP_ROOT_3="$(mktemp -d)"
TEMP_ROOT_4="$(mktemp -d)"
TEMP_ROOT_5="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT" "$TEMP_ROOT_2" "$TEMP_ROOT_3" "$TEMP_ROOT_4" "$TEMP_ROOT_5"' EXIT

mkdir -p \
  "$TEMP_ROOT/etc/juxin-orin" \
  "$TEMP_ROOT/etc" \
  "$TEMP_ROOT/sys/devices/soc0" \
  "$TEMP_ROOT/sys/class/net/eth0" \
  "$TEMP_ROOT/proc"

cat >"$TEMP_ROOT/etc/juxin-orin/image.env" <<'EOF'
ORIN_API_BASE_URL=https://nvidia.juxinsuanli.cn
ORIN_AGENT_VERSION=0.5.0-orin
ORIN_IMAGE_VERSION=orin-l4t-36.4.7-test
ORIN_HEARTBEAT_INTERVAL=45
ORIN_RECONNECT_INTERVAL=7
EOF
cat >"$TEMP_ROOT/etc/hosts" <<'EOF'
127.0.0.1 localhost
127.0.1.1 template
EOF
printf '1420823098765\n' >"$TEMP_ROOT/sys/devices/soc0/serial_number"
printf '3c:6d:66:c3:9b:bc\n' >"$TEMP_ROOT/sys/class/net/eth0/address"

ORIN_ROOT_PREFIX="$TEMP_ROOT" \
ORIN_SYS_ROOT="$TEMP_ROOT/sys" \
ORIN_PROC_ROOT="$TEMP_ROOT/proc" \
ORIN_SKIP_PLATFORM_CHECK=1 \
  "$IMAGE_DIR/agent/orin-firstboot.sh"

expected_digest="$(printf 'juxin-orin-v2|serial:1420823098765' | sha256sum | awk '{print toupper($1)}')"
expected_sn="ORIN-${expected_digest:0:16}"

[[ "$(cat "$TEMP_ROOT/var/lib/juxin-orin/device-sn")" == "$expected_sn" ]]
[[ "$(cat "$TEMP_ROOT/var/lib/juxin-orin/hardware-fingerprint")" == "$expected_digest" ]]
grep -qx "ORIN_DEVICE_SN=${expected_sn}" "$TEMP_ROOT/etc/juxin-orin/agent.env"
grep -qx "ORIN_HARDWARE_FINGERPRINT=${expected_digest}" "$TEMP_ROOT/etc/juxin-orin/agent.env"
! grep -q '^ORIN_IMAGE_LICENSE=' "$TEMP_ROOT/etc/juxin-orin/agent.env"
grep -qx 'ORIN_TASK_POLL_INTERVAL=60' "$TEMP_ROOT/etc/juxin-orin/agent.env"
grep -qx 'ORIN_TASK_TIMEOUT=240' "$TEMP_ROOT/etc/juxin-orin/agent.env"
grep -qx 'ORIN_REQUEST_RETRIES=2' "$TEMP_ROOT/etc/juxin-orin/agent.env"
grep -qx 'ORIN_RECONNECT_INTERVAL=7' "$TEMP_ROOT/etc/juxin-orin/agent.env"
grep -qx 'orin-l4t-36.4.7-test' "$TEMP_ROOT/var/lib/juxin-orin/provisioned"
[[ ! -e "$TEMP_ROOT/var/lib/juxin-orin/device-token" ]]

first_sn="$(cat "$TEMP_ROOT/var/lib/juxin-orin/device-sn")"
ORIN_ROOT_PREFIX="$TEMP_ROOT" \
ORIN_SYS_ROOT="$TEMP_ROOT/sys" \
ORIN_PROC_ROOT="$TEMP_ROOT/proc" \
ORIN_SKIP_PLATFORM_CHECK=1 \
  "$IMAGE_DIR/agent/orin-firstboot.sh"
[[ "$(cat "$TEMP_ROOT/var/lib/juxin-orin/device-sn")" == "$first_sn" ]]

mkdir -p \
  "$TEMP_ROOT_2/etc/juxin-orin" \
  "$TEMP_ROOT_2/etc" \
  "$TEMP_ROOT_2/sys/devices/soc0" \
  "$TEMP_ROOT_2/sys/class/net/eth0" \
  "$TEMP_ROOT_2/proc"
cp "$TEMP_ROOT/etc/juxin-orin/image.env" "$TEMP_ROOT_2/etc/juxin-orin/image.env"
cp "$TEMP_ROOT/etc/hosts" "$TEMP_ROOT_2/etc/hosts"
printf '1420823099999\n' >"$TEMP_ROOT_2/sys/devices/soc0/serial_number"
printf '3c:6d:66:c3:9b:bd\n' >"$TEMP_ROOT_2/sys/class/net/eth0/address"

ORIN_ROOT_PREFIX="$TEMP_ROOT_2" \
ORIN_SYS_ROOT="$TEMP_ROOT_2/sys" \
ORIN_PROC_ROOT="$TEMP_ROOT_2/proc" \
ORIN_SKIP_PLATFORM_CHECK=1 \
  "$IMAGE_DIR/agent/orin-firstboot.sh"

second_sn="$(cat "$TEMP_ROOT_2/var/lib/juxin-orin/device-sn")"
[[ "$second_sn" =~ ^ORIN-[A-F0-9]{16}$ ]]
[[ "$second_sn" != "$first_sn" ]]

mkdir -p \
  "$TEMP_ROOT_3/etc/juxin-orin" \
  "$TEMP_ROOT_3/etc" \
  "$TEMP_ROOT_3/sys/devices/soc0" \
  "$TEMP_ROOT_3/sys/class/net/eth0" \
  "$TEMP_ROOT_3/proc"
cp "$TEMP_ROOT/etc/juxin-orin/image.env" "$TEMP_ROOT_3/etc/juxin-orin/image.env"
cp "$TEMP_ROOT/etc/hosts" "$TEMP_ROOT_3/etc/hosts"
printf '1420823098765\n' >"$TEMP_ROOT_3/sys/devices/soc0/serial_number"
printf '3c:6d:66:c3:9b:ff\n' >"$TEMP_ROOT_3/sys/class/net/eth0/address"

ORIN_ROOT_PREFIX="$TEMP_ROOT_3" \
ORIN_SYS_ROOT="$TEMP_ROOT_3/sys" \
ORIN_PROC_ROOT="$TEMP_ROOT_3/proc" \
ORIN_SKIP_PLATFORM_CHECK=1 \
  "$IMAGE_DIR/agent/orin-firstboot.sh"

[[ "$(cat "$TEMP_ROOT_3/var/lib/juxin-orin/device-sn")" == "$first_sn" ]]
[[ "$(cat "$TEMP_ROOT_3/var/lib/juxin-orin/hardware-fingerprint")" == "$expected_digest" ]]

mkdir -p \
  "$TEMP_ROOT_4/etc/juxin-orin" \
  "$TEMP_ROOT_4/etc" \
  "$TEMP_ROOT_4/var/lib/juxin-orin" \
  "$TEMP_ROOT_4/sys/devices/soc0" \
  "$TEMP_ROOT_4/sys/class/net/eth0" \
  "$TEMP_ROOT_4/proc"
cp "$TEMP_ROOT/etc/juxin-orin/image.env" "$TEMP_ROOT_4/etc/juxin-orin/image.env"
cp "$TEMP_ROOT/etc/hosts" "$TEMP_ROOT_4/etc/hosts"
printf 'ORIN-ABCDEF012345\n' >"$TEMP_ROOT_4/var/lib/juxin-orin/device-sn"
printf '%064d\n' 0 >"$TEMP_ROOT_4/var/lib/juxin-orin/hardware-fingerprint"
printf '1420823090000\n' >"$TEMP_ROOT_4/sys/devices/soc0/serial_number"
printf '3c:6d:66:c3:9b:00\n' >"$TEMP_ROOT_4/sys/class/net/eth0/address"

ORIN_ROOT_PREFIX="$TEMP_ROOT_4" \
ORIN_SYS_ROOT="$TEMP_ROOT_4/sys" \
ORIN_PROC_ROOT="$TEMP_ROOT_4/proc" \
ORIN_SKIP_PLATFORM_CHECK=1 \
  "$IMAGE_DIR/agent/orin-firstboot.sh"

[[ "$(cat "$TEMP_ROOT_4/var/lib/juxin-orin/device-sn")" == "ORIN-ABCDEF012345" ]]

mkdir -p \
  "$TEMP_ROOT_5/etc/juxin-orin" \
  "$TEMP_ROOT_5/etc" \
  "$TEMP_ROOT_5/sys/class/net/docker0" \
  "$TEMP_ROOT_5/sys/class/net/eth0/device" \
  "$TEMP_ROOT_5/proc"
cp "$TEMP_ROOT/etc/juxin-orin/image.env" "$TEMP_ROOT_5/etc/juxin-orin/image.env"
cp "$TEMP_ROOT/etc/hosts" "$TEMP_ROOT_5/etc/hosts"
printf '02:42:ac:11:00:02\n' >"$TEMP_ROOT_5/sys/class/net/docker0/address"
printf '3c:6d:66:c3:9b:bc\n' >"$TEMP_ROOT_5/sys/class/net/eth0/address"

ORIN_ROOT_PREFIX="$TEMP_ROOT_5" \
ORIN_SYS_ROOT="$TEMP_ROOT_5/sys" \
ORIN_PROC_ROOT="$TEMP_ROOT_5/proc" \
ORIN_SKIP_PLATFORM_CHECK=1 \
  "$IMAGE_DIR/agent/orin-firstboot.sh"

fallback_digest="$(printf 'juxin-orin-v2|mac:3C6D66C39BBC' | sha256sum | awk '{print toupper($1)}')"
[[ "$(cat "$TEMP_ROOT_5/var/lib/juxin-orin/device-sn")" == "ORIN-${fallback_digest:0:16}" ]]
[[ "$(cat "$TEMP_ROOT_5/var/lib/juxin-orin/hardware-fingerprint")" == "$fallback_digest" ]]

echo "firstboot identity test: PASS (${first_sn}, ${second_sn}, legacy compatible)"
