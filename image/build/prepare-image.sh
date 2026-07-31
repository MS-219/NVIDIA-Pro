#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOWNLOADS=""
WORK_DIR=""
SSH_PUBLIC_KEY_FILE=""
API_URL="https://nvidia.juxinsuanli.cn"
IMAGE_VERSION="orin-l4t-36.4.7-v1"
AGENT_VERSION="0.5.0-orin"
PROMPT_MAINTENANCE_PASSWORD=0
QUIET_BOOT=0

BSP_NAME="Jetson_Linux_R36.4.4_aarch64.tbz2"
BSP_SHA1="1039c377717e443cbabd9a1a719162dd84ab4678"
ROOTFS_NAME="Tegra_Linux_Sample-Root-Filesystem_R36.4.4_aarch64.tbz2"
ROOTFS_SHA1="73df3f66ad77f29d1424d61dbb45d5587090c912"

usage() {
  cat <<'EOF'
Usage: sudo ./prepare-image.sh --downloads DIR --work-dir DIR \
  --ssh-public-key FILE [options]

The script must run on a physical x86_64 Ubuntu 22.04 host and an ext4/xfs/btrfs
work directory. It builds NVIDIA's Basic (non-GUI) rootfs and injects the Orin agent.
EOF
}

die() {
  echo "prepare-image: $*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --downloads) DOWNLOADS="${2:-}"; shift 2 ;;
    --work-dir) WORK_DIR="${2:-}"; shift 2 ;;
    --ssh-public-key) SSH_PUBLIC_KEY_FILE="${2:-}"; shift 2 ;;
    --api-url) API_URL="${2:-}"; shift 2 ;;
    --image-version) IMAGE_VERSION="${2:-}"; shift 2 ;;
    --agent-version) AGENT_VERSION="${2:-}"; shift 2 ;;
    --prompt-maintenance-password) PROMPT_MAINTENANCE_PASSWORD=1; shift ;;
    --quiet-boot) QUIET_BOOT=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ "$EUID" -eq 0 ]] || die "run as root"
[[ "$(uname -s)" == "Linux" && "$(uname -m)" == "x86_64" ]] || die "requires x86_64 Linux"
grep -q '^VERSION_CODENAME=jammy$' /etc/os-release || die "requires Ubuntu 22.04 (jammy)"
[[ -n "$DOWNLOADS" && -d "$DOWNLOADS" ]] || die "--downloads directory is required"
[[ -n "$WORK_DIR" ]] || die "--work-dir is required"
[[ -f "$DOWNLOADS/$BSP_NAME" ]] || die "missing $DOWNLOADS/$BSP_NAME"
[[ -f "$SSH_PUBLIC_KEY_FILE" ]] || die "--ssh-public-key is required"

install -d -m 0755 "$WORK_DIR"
WORK_DIR="$(realpath "$WORK_DIR")"
[[ "$WORK_DIR" != "/" ]] || die "refusing to use the build host root filesystem"
fs_type="$(findmnt -no FSTYPE --target "$WORK_DIR" 2>/dev/null || true)"
case "$fs_type" in
  ext4|xfs|btrfs) ;;
  *) die "work directory must use ext4, xfs, or btrfs; detected ${fs_type:-unknown}" ;;
esac

[[ ! -e "$WORK_DIR/Linux_for_Tegra" ]] || die "$WORK_DIR/Linux_for_Tegra already exists; use a fresh work directory"

verify_sha1() {
  local expected="$1" file="$2" actual
  actual="$(sha1sum "$file" | awk '{print $1}')"
  [[ "$actual" == "$expected" ]] || die "checksum mismatch: $file"
}

verify_sha1 "$BSP_SHA1" "$DOWNLOADS/$BSP_NAME"
if [[ -f "$DOWNLOADS/$ROOTFS_NAME" ]]; then
  verify_sha1 "$ROOTFS_SHA1" "$DOWNLOADS/$ROOTFS_NAME"
  echo "Verified the official desktop Sample RootFS archive; the product build uses NVIDIA Basic flavor instead."
fi

echo "Extracting NVIDIA BSP..."
tar --numeric-owner -xpf "$DOWNLOADS/$BSP_NAME" -C "$WORK_DIR"
L4T_DIR="$WORK_DIR/Linux_for_Tegra"

cd "$L4T_DIR"
./tools/l4t_flash_prerequisites.sh

echo "Building NVIDIA Basic Ubuntu 22.04 rootfs (no desktop)..."
cd "$L4T_DIR/tools/samplefs"
./nv_build_samplefs.sh --abi aarch64 --distro ubuntu --flavor basic --version jammy
[[ -s sample_fs.tbz2 ]] || die "NVIDIA basic rootfs generation failed"

echo "Installing Basic rootfs..."
find "$L4T_DIR/rootfs" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
tar --numeric-owner -xpf sample_fs.tbz2 -C "$L4T_DIR/rootfs"

cd "$L4T_DIR"
./apply_binaries.sh

"$SCRIPT_DIR/install-jetpack-runtime.sh" --rootfs "$L4T_DIR/rootfs"

inject_args=( \
  --rootfs "$L4T_DIR/rootfs" \
  --ssh-public-key "$SSH_PUBLIC_KEY_FILE" \
  --api-url "$API_URL" \
  --image-version "$IMAGE_VERSION" \
  --agent-version "$AGENT_VERSION" \
)
[[ "$PROMPT_MAINTENANCE_PASSWORD" == "1" ]] && inject_args+=(--prompt-maintenance-password)
[[ "$QUIET_BOOT" == "1" ]] && inject_args+=(--quiet-boot)
"$SCRIPT_DIR/inject-rootfs.sh" "${inject_args[@]}"

echo
echo "Rootfs preparation complete: $L4T_DIR/rootfs"
echo "Next: put one Orin in Recovery mode and run flash-nvme.sh."
