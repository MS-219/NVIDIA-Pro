#!/usr/bin/env bash
set -Eeuo pipefail

umask 077

ROOT_PREFIX="${ORIN_ROOT_PREFIX:-}"
SYS_ROOT="${ORIN_SYS_ROOT:-${ROOT_PREFIX}/sys}"
PROC_ROOT="${ORIN_PROC_ROOT:-${ROOT_PREFIX}/proc}"
ETC_DIR="${ROOT_PREFIX}/etc/juxin-orin"
STATE_DIR="${ROOT_PREFIX}/var/lib/juxin-orin"
IMAGE_ENV="${ETC_DIR}/image.env"
AGENT_ENV="${ETC_DIR}/agent.env"
MARKER="${STATE_DIR}/provisioned"

log() {
  printf 'juxin-orin-firstboot: %s\n' "$*"
}

die() {
  log "ERROR: $*" >&2
  exit 1
}

read_value() {
  local key="$1"
  local fallback="${2:-}"
  local value=""
  if [[ -f "$IMAGE_ENV" ]]; then
    value="$(sed -n "s/^${key}=//p" "$IMAGE_ENV" | tail -n 1 | tr -d '\r')"
  fi
  printf '%s' "${value:-$fallback}"
}

read_first_identifier() {
  local path value
  for path in \
    "${SYS_ROOT}/devices/soc0/serial_number" \
    "${SYS_ROOT}/firmware/devicetree/base/serial-number" \
    "${PROC_ROOT}/device-tree/serial-number"; do
    if [[ -r "$path" ]]; then
      value="$(tr -d '\000\r\n ' <"$path" 2>/dev/null || true)"
      if [[ -n "$value" && "$value" != "0000000000000000" ]]; then
        printf '%s' "$value"
        return 0
      fi
    fi
  done
  return 1
}

read_first_mac() {
  local interface value
  while IFS= read -r interface; do
    [[ "$(basename "$interface")" == "lo" ]] && continue
    [[ -r "$interface/address" ]] || continue
    value="$(tr '[:lower:]' '[:upper:]' <"$interface/address" | tr -d ':\r\n ')"
    if [[ "$value" =~ ^[A-F0-9]{12}$ && "$value" != "000000000000" ]]; then
      printf '%s' "$value"
      return 0
    fi
  done < <(find "${SYS_ROOT}/class/net" -mindepth 1 -maxdepth 1 \( -type l -o -type d \) 2>/dev/null | sort)
  return 1
}

platform_check() {
  [[ "${ORIN_SKIP_PLATFORM_CHECK:-0}" == "1" ]] && return 0
  [[ "$(uname -m)" == "aarch64" ]] || die "this image supports aarch64 only"
  [[ -s "${ROOT_PREFIX}/etc/nv_tegra_release" ]] || die "NVIDIA L4T release metadata is missing"
}

generate_identity() {
  local hardware_id="" mac="" seed="" digest=""
  hardware_id="$(read_first_identifier || true)"
  mac="$(read_first_mac || true)"

  if [[ -z "$hardware_id" && -z "$mac" ]]; then
    if [[ ! -s "${STATE_DIR}/device-seed" ]]; then
      od -An -N32 -tx1 /dev/urandom | tr -d ' \n' >"${STATE_DIR}/device-seed"
    fi
    seed="$(cat "${STATE_DIR}/device-seed")"
  else
    seed="${hardware_id}|${mac}"
  fi

  digest="$(printf 'juxin-orin-v1|%s' "$seed" | sha256sum | awk '{print toupper($1)}')"
  printf 'ORIN-%s\n%s\n' "${digest:0:12}" "$digest"
}

configure_hostname() {
  local sn="$1"
  local hostname="juxin-orin-$(printf '%s' "${sn: -8}" | tr '[:upper:]' '[:lower:]')"
  printf '%s\n' "$hostname" >"${ROOT_PREFIX}/etc/hostname"
  if grep -qE '^127\.0\.1\.1([[:space:]]|$)' "${ROOT_PREFIX}/etc/hosts" 2>/dev/null; then
    sed -i -E "s/^127\.0\.1\.1.*/127.0.1.1\t${hostname}/" "${ROOT_PREFIX}/etc/hosts"
  else
    printf '127.0.1.1\t%s\n' "$hostname" >>"${ROOT_PREFIX}/etc/hosts"
  fi
  if [[ -z "$ROOT_PREFIX" ]] && command -v hostnamectl >/dev/null 2>&1; then
    hostnamectl set-hostname "$hostname" || true
  fi
}

initialize_host_identity() {
  [[ -n "$ROOT_PREFIX" ]] && return 0
  if [[ ! -s /etc/machine-id ]] && command -v systemd-machine-id-setup >/dev/null 2>&1; then
    systemd-machine-id-setup
  fi
  if command -v ssh-keygen >/dev/null 2>&1; then
    ssh-keygen -A
  fi
}

main() {
  local api_url image_license agent_version image_version interval task_interval task_timeout request_retries
  local identity sn fingerprint

  platform_check
  [[ -f "$IMAGE_ENV" ]] || die "missing ${IMAGE_ENV}"
  install -d -m 0755 "$ETC_DIR" "$STATE_DIR"

  api_url="$(read_value ORIN_API_BASE_URL https://nvidia.juxinsuanli.cn)"
  image_license="$(read_value ORIN_IMAGE_LICENSE)"
  agent_version="$(read_value ORIN_AGENT_VERSION 0.3.0-orin)"
  image_version="$(read_value ORIN_IMAGE_VERSION orin-l4t-36.4.7-v1)"
  interval="$(read_value ORIN_HEARTBEAT_INTERVAL 60)"
  task_interval="$(read_value ORIN_TASK_POLL_INTERVAL 60)"
  task_timeout="$(read_value ORIN_TASK_TIMEOUT 240)"
  request_retries="$(read_value ORIN_REQUEST_RETRIES 2)"

  [[ "$api_url" =~ ^https://[A-Za-z0-9.-]+(:[0-9]+)?(/.*)?$ ]] || die "API URL must use HTTPS"
  [[ "$image_license" =~ ^IMG-[0-9]{8}-[A-F0-9]{24}$ ]] || die "image license format is invalid"
  [[ "$agent_version" =~ ^[A-Za-z0-9._-]+$ ]] || die "agent version format is invalid"
  [[ "$image_version" =~ ^[A-Za-z0-9._-]+$ ]] || die "image version format is invalid"
  [[ "$interval" =~ ^[0-9]+$ ]] || die "heartbeat interval is invalid"
  [[ "$task_interval" =~ ^[0-9]+$ ]] || die "task poll interval is invalid"
  [[ "$task_timeout" =~ ^[0-9]+$ ]] || die "task timeout is invalid"
  [[ "$request_retries" =~ ^[0-9]+$ ]] || die "request retries is invalid"

  if [[ -s "${STATE_DIR}/device-sn" && -s "${STATE_DIR}/hardware-fingerprint" ]]; then
    sn="$(cat "${STATE_DIR}/device-sn")"
    fingerprint="$(cat "${STATE_DIR}/hardware-fingerprint")"
  else
    identity="$(generate_identity)"
    sn="$(printf '%s\n' "$identity" | sed -n '1p')"
    fingerprint="$(printf '%s\n' "$identity" | sed -n '2p')"
    printf '%s\n' "$sn" >"${STATE_DIR}/device-sn"
    printf '%s\n' "$fingerprint" >"${STATE_DIR}/hardware-fingerprint"
  fi

  [[ "$sn" =~ ^ORIN-[A-F0-9]{12}$ ]] || die "generated device SN is invalid"
  [[ "$fingerprint" =~ ^[A-F0-9]{64}$ ]] || die "generated fingerprint is invalid"

  cat >"$AGENT_ENV" <<EOF
ORIN_API_BASE_URL=${api_url}
ORIN_DEVICE_SN=${sn}
ORIN_HARDWARE_FINGERPRINT=${fingerprint}
ORIN_IMAGE_LICENSE=${image_license}
ORIN_AGENT_VERSION=${agent_version}
ORIN_IMAGE_VERSION=${image_version}
ORIN_HEARTBEAT_INTERVAL=${interval}
ORIN_TASK_POLL_INTERVAL=${task_interval}
ORIN_TASK_TIMEOUT=${task_timeout}
ORIN_REQUEST_RETRIES=${request_retries}
ORIN_STATE_DIR=/var/lib/juxin-orin
EOF
  chmod 0600 "$AGENT_ENV" "${STATE_DIR}/device-sn" "${STATE_DIR}/hardware-fingerprint"

  configure_hostname "$sn"
  initialize_host_identity
  printf '%s\n' "$image_version" >"$MARKER"
  chmod 0600 "$MARKER"
  sync
  log "provisioned ${sn}; agent will enroll with the Orin platform"
}

main "$@"
