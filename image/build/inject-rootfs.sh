#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ROOTFS=""
SSH_PUBLIC_KEY_FILE=""
API_URL="https://nvidia.juxinsuanli.cn"
IMAGE_VERSION="orin-l4t-36.4.7-v1"
AGENT_VERSION="0.5.0-orin"
MAINTENANCE_USER="juxin"
PROMPT_MAINTENANCE_PASSWORD=0
MAINTENANCE_PASSWORD_HASH="!"
QUIET_BOOT=0

usage() {
  cat <<'EOF'
Usage: sudo ./inject-rootfs.sh --rootfs PATH --ssh-public-key PATH [options]

Options:
  --api-url URL             Orin platform URL (default: https://nvidia.juxinsuanli.cn)
  --image-version VERSION   Image version stored in every heartbeat
  --agent-version VERSION   Agent version stored in every heartbeat
  --maintenance-user USER   SSH maintenance account (default: juxin)
  --prompt-maintenance-password
                              Prompt for a shared sudo password (minimum 8 characters)
  --quiet-boot              Hide Linux boot output before the status display
EOF
}

die() {
  echo "inject-rootfs: $*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --rootfs) ROOTFS="${2:-}"; shift 2 ;;
    --ssh-public-key) SSH_PUBLIC_KEY_FILE="${2:-}"; shift 2 ;;
    --api-url) API_URL="${2:-}"; shift 2 ;;
    --image-version) IMAGE_VERSION="${2:-}"; shift 2 ;;
    --agent-version) AGENT_VERSION="${2:-}"; shift 2 ;;
    --maintenance-user) MAINTENANCE_USER="${2:-}"; shift 2 ;;
    --prompt-maintenance-password) PROMPT_MAINTENANCE_PASSWORD=1; shift ;;
    --quiet-boot) QUIET_BOOT=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ "$EUID" -eq 0 ]] || die "run as root"
[[ -n "$ROOTFS" && -d "$ROOTFS/etc" && -f "$ROOTFS/etc/os-release" ]] || die "invalid rootfs"
ROOTFS="$(realpath "$ROOTFS")"
[[ "$ROOTFS" != "/" ]] || die "refusing to modify the build host root filesystem"
[[ -n "$SSH_PUBLIC_KEY_FILE" && -f "$SSH_PUBLIC_KEY_FILE" ]] || die "--ssh-public-key is required"
[[ ! -e "$ROOTFS/usr/bin/gnome-shell" ]] || die "desktop rootfs detected; build NVIDIA basic flavor instead"
[[ "$API_URL" =~ ^https://[A-Za-z0-9.-]+(:[0-9]+)?(/.*)?$ ]] || die "API URL must use HTTPS"
[[ "$IMAGE_VERSION" =~ ^[A-Za-z0-9._-]+$ ]] || die "invalid image version"
[[ "$AGENT_VERSION" =~ ^[A-Za-z0-9._-]+$ ]] || die "invalid agent version"
[[ "$MAINTENANCE_USER" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]] || die "invalid maintenance username"

SSH_PUBLIC_KEY="$(head -n 1 "$SSH_PUBLIC_KEY_FILE" | tr -d '\r\n')"
[[ "$SSH_PUBLIC_KEY" =~ ^ssh-(ed25519|rsa)[[:space:]]+[A-Za-z0-9+/=]+([[:space:]].*)?$ ]] \
  || die "SSH public key must be an OpenSSH ed25519 or RSA key"

if [[ "$PROMPT_MAINTENANCE_PASSWORD" == "1" ]]; then
  command -v openssl >/dev/null 2>&1 || die "openssl is required to set the maintenance password"
  [[ -t 0 ]] || die "--prompt-maintenance-password requires an interactive terminal"
  read -r -s -p "Maintenance sudo password (minimum 8 characters): " maintenance_password
  printf '\n' >&2
  read -r -s -p "Confirm maintenance sudo password: " maintenance_password_confirmation
  printf '\n' >&2
  [[ ${#maintenance_password} -ge 8 ]] || die "maintenance password must contain at least 8 characters"
  [[ "$maintenance_password" == "$maintenance_password_confirmation" ]] \
    || die "maintenance password confirmation does not match"
  MAINTENANCE_PASSWORD_HASH="$(printf '%s\n' "$maintenance_password" | openssl passwd -6 -stdin)"
  unset maintenance_password maintenance_password_confirmation
  [[ "$MAINTENANCE_PASSWORD_HASH" == \$6\$* ]] \
    || die "failed to generate a SHA-512 maintenance password hash"
fi

install -d -m 0755 \
  "$ROOTFS/opt/juxin-orin/agent" \
  "$ROOTFS/opt/juxin-orin/display" \
  "$ROOTFS/opt/juxin-orin/models" \
  "$ROOTFS/opt/juxin-orin/runtime" \
  "$ROOTFS/usr/lib/juxin-orin" \
  "$ROOTFS/etc/juxin-orin" \
  "$ROOTFS/var/lib/juxin-orin" \
  "$ROOTFS/var/log/juxin-orin" \
  "$ROOTFS/etc/systemd/system/multi-user.target.wants" \
  "$ROOTFS/etc/systemd/network" \
  "$ROOTFS/etc/ssh/sshd_config.d"

install -m 0755 "$IMAGE_DIR/agent/orin_agent.py" "$ROOTFS/opt/juxin-orin/agent/orin_agent.py"
install -m 0755 "$IMAGE_DIR/agent/orin_display.py" "$ROOTFS/opt/juxin-orin/display/orin_display.py"
install -m 0755 "$IMAGE_DIR/agent/orin-firstboot.sh" "$ROOTFS/usr/lib/juxin-orin/orin-firstboot.sh"
install -m 0755 "$IMAGE_DIR/agent/orin-performance.sh" "$ROOTFS/usr/lib/juxin-orin/orin-performance.sh"
install -m 0644 "$IMAGE_DIR/agent/juxin-orin-agent.service" "$ROOTFS/etc/systemd/system/juxin-orin-agent.service"
install -m 0644 "$IMAGE_DIR/agent/juxin-orin-display.service" "$ROOTFS/etc/systemd/system/juxin-orin-display.service"
install -m 0644 "$IMAGE_DIR/agent/juxin-orin-firstboot.service" "$ROOTFS/etc/systemd/system/juxin-orin-firstboot.service"
install -m 0644 "$IMAGE_DIR/agent/juxin-orin-performance.service" "$ROOTFS/etc/systemd/system/juxin-orin-performance.service"

cat >"$ROOTFS/etc/juxin-orin/image.env" <<EOF
ORIN_API_BASE_URL=${API_URL}
ORIN_AGENT_VERSION=${AGENT_VERSION}
ORIN_IMAGE_VERSION=${IMAGE_VERSION}
ORIN_HEARTBEAT_INTERVAL=60
ORIN_RECONNECT_INTERVAL=5
ORIN_TASK_POLL_INTERVAL=60
ORIN_TASK_TIMEOUT=240
ORIN_REQUEST_RETRIES=2
EOF
chmod 0600 "$ROOTFS/etc/juxin-orin/image.env"

cat >"$ROOTFS/etc/juxin-orin/image-release" <<EOF
IMAGE_VERSION=${IMAGE_VERSION}
AGENT_VERSION=${AGENT_VERSION}
L4T_BASE=36.4.7
ROOTFS_FLAVOR=basic
NVIDIA_BSP=36.4.4
L4T_PACKAGE_VERSION=36.4.7-20250918154033
POWER_MODE=backend-managed
FAN_PROFILE=cool
CONTAINER_RUNTIME=nvidia-container-toolkit
EOF
chmod 0644 "$ROOTFS/etc/juxin-orin/image-release"

cat >"$ROOTFS/etc/systemd/network/20-juxin-wired.network" <<'EOF'
[Match]
Name=eth* en*

[Network]
DHCP=yes
IPv6AcceptRA=yes
EOF

cat >"$ROOTFS/etc/ssh/sshd_config.d/20-juxin-hardening.conf" <<'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
EOF

if ! grep -qE "^${MAINTENANCE_USER}:" "$ROOTFS/etc/passwd"; then
  groups=()
  for group in sudo adm video render docker; do
    grep -qE "^${group}:" "$ROOTFS/etc/group" && groups+=("$group")
  done
  group_list="$(IFS=,; printf '%s' "${groups[*]}")"
  useradd_args=(--root "$ROOTFS" --create-home --shell /bin/bash --password '!')
  [[ -n "$group_list" ]] && useradd_args+=(--groups "$group_list")
  useradd_args+=("$MAINTENANCE_USER")
  useradd "${useradd_args[@]}"
fi
if [[ "$PROMPT_MAINTENANCE_PASSWORD" == "1" ]]; then
  usermod --root "$ROOTFS" --password "$MAINTENANCE_PASSWORD_HASH" "$MAINTENANCE_USER"
fi

user_home="$ROOTFS/home/$MAINTENANCE_USER"
install -d -m 0700 "$user_home/.ssh"
printf '%s\n' "$SSH_PUBLIC_KEY" >"$user_home/.ssh/authorized_keys"
chmod 0600 "$user_home/.ssh/authorized_keys"
user_ids="$(awk -F: -v user="$MAINTENANCE_USER" '$1 == user {print $3 ":" $4}' "$ROOTFS/etc/passwd")"
[[ -n "$user_ids" ]] || die "failed to resolve maintenance user IDs"
chown -R "$user_ids" "$user_home"

ln -sfn /etc/systemd/system/juxin-orin-firstboot.service \
  "$ROOTFS/etc/systemd/system/multi-user.target.wants/juxin-orin-firstboot.service"
ln -sfn /etc/systemd/system/juxin-orin-agent.service \
  "$ROOTFS/etc/systemd/system/multi-user.target.wants/juxin-orin-agent.service"
ln -sfn /etc/systemd/system/juxin-orin-display.service \
  "$ROOTFS/etc/systemd/system/multi-user.target.wants/juxin-orin-display.service"
ln -sfn /etc/systemd/system/juxin-orin-performance.service \
  "$ROOTFS/etc/systemd/system/multi-user.target.wants/juxin-orin-performance.service"
ln -sfn /lib/systemd/system/systemd-networkd.service \
  "$ROOTFS/etc/systemd/system/multi-user.target.wants/systemd-networkd.service"
ln -sfn /lib/systemd/system/systemd-resolved.service \
  "$ROOTFS/etc/systemd/system/multi-user.target.wants/systemd-resolved.service"
ln -sfn /lib/systemd/system/ssh.service \
  "$ROOTFS/etc/systemd/system/multi-user.target.wants/ssh.service"
rm -f "$ROOTFS/etc/systemd/system/default.target"
ln -s /lib/systemd/system/multi-user.target "$ROOTFS/etc/systemd/system/default.target"

rm -f "$ROOTFS/etc/systemd/system/display-manager.service"
rm -f "$ROOTFS/etc/systemd/system/getty.target.wants/getty@tty1.service"
ln -sfn /dev/null "$ROOTFS/etc/systemd/system/getty@tty1.service"

if [[ "$QUIET_BOOT" == "1" ]]; then
  shopt -s nullglob
  boot_configs=("$ROOTFS"/boot/extlinux/extlinux.conf "$ROOTFS"/boot/extlinux/*.conf)
  shopt -u nullglob
  for boot_config in "${boot_configs[@]}"; do
    sed -i -E '/^[[:space:]]*APPEND[[:space:]]/ { /(^|[[:space:]])quiet([[:space:]]|$)/! s/[[:space:]]*$/ quiet loglevel=0 systemd.show_status=false vt.global_cursor_default=0/; }' "$boot_config"
  done
fi

rm -f "$ROOTFS/etc/ssh/ssh_host_"*
rm -f \
  "$ROOTFS/etc/juxin-orin/agent.env" \
  "$ROOTFS/var/lib/juxin-orin/device-sn" \
  "$ROOTFS/var/lib/juxin-orin/hardware-fingerprint" \
  "$ROOTFS/var/lib/juxin-orin/bind-code" \
  "$ROOTFS/var/lib/juxin-orin/device-token" \
  "$ROOTFS/var/lib/juxin-orin/display-status.json" \
  "$ROOTFS/var/lib/juxin-orin/device-seed" \
  "$ROOTFS/var/lib/juxin-orin/provisioned"

: >"$ROOTFS/etc/machine-id"
install -d -m 0755 "$ROOTFS/var/lib/dbus" "$ROOTFS/etc/nv"
rm -f "$ROOTFS/var/lib/dbus/machine-id"
ln -s /etc/machine-id "$ROOTFS/var/lib/dbus/machine-id"
touch "$ROOTFS/etc/nv/nvautoconfig"

echo "Injected Juxin Orin ${IMAGE_VERSION} into $ROOTFS"
if [[ "$PROMPT_MAINTENANCE_PASSWORD" == "1" ]]; then
  echo "Configured a shared sudo password for maintenance user ${MAINTENANCE_USER}."
fi
echo "The image contains no device SN, device token, machine-id, or SSH host key."
