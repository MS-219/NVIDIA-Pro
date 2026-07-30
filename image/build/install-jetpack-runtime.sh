#!/usr/bin/env bash
set -Eeuo pipefail

ROOTFS=""
JETPACK_VERSION="6.2.1+b38"
L4T_VERSION="36.4.7-20250918154033"
MOUNTS=()
RESOLV_BACKUP=""
POLICY_BACKUP=""
QEMU_INSTALLED=0
RESOLV_INSTALLED=0
POLICY_INSTALLED=0

usage() {
  echo "Usage: sudo ./install-jetpack-runtime.sh --rootfs PATH [--version 6.2.1+b38] [--l4t-version 36.4.7-20250918154033]"
}

die() {
  echo "install-jetpack-runtime: $*" >&2
  exit 1
}

cleanup() {
  set +e
  local index
  [[ -n "${ROOTFS:-}" && "$ROOTFS" != "/" ]] || return 0
  for ((index=${#MOUNTS[@]}-1; index>=0; index--)); do
    umount "${MOUNTS[$index]}" 2>/dev/null || true
  done
  [[ "$RESOLV_INSTALLED" == "1" ]] && rm -f "$ROOTFS/etc/resolv.conf"
  [[ -n "$RESOLV_BACKUP" && ( -e "$RESOLV_BACKUP" || -L "$RESOLV_BACKUP" ) ]] && {
    mv "$RESOLV_BACKUP" "$ROOTFS/etc/resolv.conf"
  }
  [[ -n "$POLICY_BACKUP" && -e "$POLICY_BACKUP" ]] && {
    [[ "$POLICY_INSTALLED" == "1" ]] && rm -f "$ROOTFS/usr/sbin/policy-rc.d"
    mv "$POLICY_BACKUP" "$ROOTFS/usr/sbin/policy-rc.d"
  }
  [[ -z "$POLICY_BACKUP" && "$POLICY_INSTALLED" == "1" ]] \
    && rm -f "$ROOTFS/usr/sbin/policy-rc.d"
  [[ "$QEMU_INSTALLED" == "1" ]] && rm -f "$ROOTFS/usr/bin/qemu-aarch64-static"
}
trap cleanup EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    --rootfs) ROOTFS="${2:-}"; shift 2 ;;
    --version) JETPACK_VERSION="${2:-}"; shift 2 ;;
    --l4t-version) L4T_VERSION="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ "$EUID" -eq 0 ]] || die "run as root"
[[ -n "$ROOTFS" && -f "$ROOTFS/etc/os-release" ]] || die "invalid rootfs"
ROOTFS="$(realpath "$ROOTFS")"
[[ "$ROOTFS" != "/" ]] || die "refusing to use the build host root filesystem"
[[ -x "$ROOTFS/bin/bash" ]] || die "rootfs is missing /bin/bash"
[[ -x /usr/bin/qemu-aarch64-static ]] || die "qemu-user-static is required"
[[ "$JETPACK_VERSION" =~ ^[0-9.]+\+b[0-9]+$ ]] || die "invalid JetPack runtime version"
[[ "$L4T_VERSION" =~ ^36\.4\.[0-9]+-[0-9]{14}$ ]] || die "invalid L4T package version"

install -d -m 0755 "$ROOTFS/etc/apt/preferences.d"
cat >"$ROOTFS/etc/apt/preferences.d/90-juxin-l4t.pref" <<EOF
Package: nvidia-l4t-*
Pin: version ${L4T_VERSION}
Pin-Priority: 1001
EOF

install -m 0755 /usr/bin/qemu-aarch64-static "$ROOTFS/usr/bin/qemu-aarch64-static"
QEMU_INSTALLED=1

for target in dev dev/pts proc sys; do
  mkdir -p "$ROOTFS/$target"
done
mount --bind /dev "$ROOTFS/dev"; MOUNTS+=("$ROOTFS/dev")
mount --bind /dev/pts "$ROOTFS/dev/pts"; MOUNTS+=("$ROOTFS/dev/pts")
mount -t proc proc "$ROOTFS/proc"; MOUNTS+=("$ROOTFS/proc")
mount -t sysfs sysfs "$ROOTFS/sys"; MOUNTS+=("$ROOTFS/sys")

if [[ -e "$ROOTFS/etc/resolv.conf" || -L "$ROOTFS/etc/resolv.conf" ]]; then
  RESOLV_BACKUP="$ROOTFS/etc/resolv.conf.juxin-backup"
  mv "$ROOTFS/etc/resolv.conf" "$RESOLV_BACKUP"
fi
cp -L /etc/resolv.conf "$ROOTFS/etc/resolv.conf"
RESOLV_INSTALLED=1

if [[ -e "$ROOTFS/usr/sbin/policy-rc.d" ]]; then
  POLICY_BACKUP="$ROOTFS/usr/sbin/policy-rc.d.juxin-backup"
  mv "$ROOTFS/usr/sbin/policy-rc.d" "$POLICY_BACKUP"
fi
cat >"$ROOTFS/usr/sbin/policy-rc.d" <<'EOF'
#!/bin/sh
exit 101
EOF
chmod 0755 "$ROOTFS/usr/sbin/policy-rc.d"
POLICY_INSTALLED=1

chroot "$ROOTFS" /usr/bin/qemu-aarch64-static /usr/bin/env \
  DEBIAN_FRONTEND=noninteractive /bin/bash -c '
    set -e
    apt-get update
    apt-get dist-upgrade -y
    apt-get install -y --no-install-recommends \
      "nvidia-jetpack-runtime='"$JETPACK_VERSION"'" \
      "nvidia-l4t-core='"$L4T_VERSION"'" \
      docker.io \
      fonts-noto-cjk \
      python3-pil
    command -v nvidia-ctk >/dev/null
    install -d -m 0755 /etc/docker
    nvidia-ctk runtime configure --runtime=docker
    systemctl enable docker.service >/dev/null 2>&1 || true
    apt-get clean
    rm -rf /var/lib/apt/lists/*
  '

chroot "$ROOTFS" /usr/bin/qemu-aarch64-static dpkg-query \
  -W -f='${Package} ${Version}\n' \
  nvidia-jetpack-runtime nvidia-l4t-core docker.io nvidia-container-toolkit \
  fonts-noto-cjk python3-pil

installed_l4t="$(chroot "$ROOTFS" /usr/bin/qemu-aarch64-static \
  dpkg-query -W -f='${Version}' nvidia-l4t-core)"
[[ "$installed_l4t" == "$L4T_VERSION" ]] \
  || die "L4T core version mismatch: expected $L4T_VERSION, got $installed_l4t"

echo "NVIDIA JetPack runtime ${JETPACK_VERSION} installed into $ROOTFS"
