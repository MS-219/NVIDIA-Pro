#!/bin/sh
set -eu

umask 077

ROOT_PREFIX="${JUXIN_RK_ROOT_PREFIX:-}"
ETC_DIR="${ROOT_PREFIX}/etc/juxin-rk3588"
STATE_DIR="${ROOT_PREFIX}/var/lib/juxin-rk3588"
AGENT_ENV="${ETC_DIR}/agent.env"
MARKER="${STATE_DIR}/provisioned"

read_value() {
    key="$1"
    fallback="$2"
    value=""
    if [ -f "${ETC_DIR}/image.env" ]; then
        value="$(sed -n "s/^${key}=//p" "${ETC_DIR}/image.env" | tail -n 1 | tr -d '\r')"
    fi
    printf '%s' "${value:-$fallback}"
}

read_identity_seed() {
    for path in \
        "${ROOT_PREFIX}/proc/device-tree/serial-number" \
        "${ROOT_PREFIX}/sys/firmware/devicetree/base/serial-number" \
        "${ROOT_PREFIX}/sys/class/net/eth1/address" \
        "${ROOT_PREFIX}/sys/class/net/eth0/address"; do
        if [ -r "$path" ]; then
            value="$(tr -d '\000\r\n: ' <"$path" 2>/dev/null || true)"
            case "$value" in
                ""|0000000000000000) continue ;;
                *) printf '%s' "$value"; return 0 ;;
            esac
        fi
    done
    if [ ! -s "${STATE_DIR}/device-seed" ]; then
        od -An -N32 -tx1 /dev/urandom | tr -d ' \n' >"${STATE_DIR}/device-seed"
    fi
    cat "${STATE_DIR}/device-seed"
}

mkdir -p "$ETC_DIR" "$STATE_DIR"

if [ ! -s "${STATE_DIR}/device-sn" ] || [ ! -s "${STATE_DIR}/hardware-fingerprint" ]; then
    seed="$(read_identity_seed)"
    digest="$(printf 'juxin-rk3588-v1|%s' "$seed" | sha256sum | awk '{print toupper($1)}')"
    printf 'JD-%s\n' "$(printf '%s' "$digest" | cut -c1-16)" >"${STATE_DIR}/device-sn"
    printf '%s\n' "$digest" >"${STATE_DIR}/hardware-fingerprint"
fi

sn="$(tr -d '\r\n' <"${STATE_DIR}/device-sn")"
fingerprint="$(tr -d '\r\n' <"${STATE_DIR}/hardware-fingerprint")"
case "$sn" in JD-[A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9]|RK3588-[A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9][A-F0-9]) ;; *) exit 1 ;; esac
case "$fingerprint" in [A-F0-9][A-F0-9]* ) [ "${#fingerprint}" -eq 64 ] || exit 1 ;; *) exit 1 ;; esac

api_url="$(read_value JUXIN_RK_API_BASE_URL https://jd.ldjuxin.yun)"
agent_version="$(read_value JUXIN_RK_AGENT_VERSION 0.1.0-rk3588)"
image_version="$(read_value JUXIN_RK_IMAGE_VERSION rk3588-cx3588-a-dev1)"
interval="$(read_value JUXIN_RK_HEARTBEAT_INTERVAL 60)"
task_interval="$(read_value JUXIN_RK_TASK_POLL_INTERVAL 60)"

cat >"$AGENT_ENV" <<EOF
JUXIN_RK_API_BASE_URL=${api_url}
JUXIN_RK_DEVICE_SN=${sn}
JUXIN_RK_HARDWARE_FINGERPRINT=${fingerprint}
JUXIN_RK_AGENT_VERSION=${agent_version}
JUXIN_RK_IMAGE_VERSION=${image_version}
JUXIN_RK_HEARTBEAT_INTERVAL=${interval}
JUXIN_RK_TASK_POLL_INTERVAL=${task_interval}
JUXIN_RK_STATE_DIR=/var/lib/juxin-rk3588
EOF

chmod 0600 "$AGENT_ENV" "${STATE_DIR}/device-sn" "${STATE_DIR}/hardware-fingerprint"
printf '%s\n' "$image_version" >"$MARKER"
chmod 0600 "$MARKER"
sync
