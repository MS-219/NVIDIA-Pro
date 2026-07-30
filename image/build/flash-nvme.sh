#!/usr/bin/env bash
set -Eeuo pipefail

L4T_DIR=""
ASSUME_YES=0
NVME_SIZE_GB=256

usage() {
  echo "Usage: sudo ./flash-nvme.sh --l4t-dir PATH [--nvme-size-gb 256] [--yes]"
}

die() {
  echo "flash-nvme: $*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --l4t-dir) L4T_DIR="${2:-}"; shift 2 ;;
    --nvme-size-gb) NVME_SIZE_GB="${2:-}"; shift 2 ;;
    --yes) ASSUME_YES=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ "$EUID" -eq 0 ]] || die "run as root"
[[ "$(uname -s)" == "Linux" && "$(uname -m)" == "x86_64" ]] || die "requires x86_64 Linux"
[[ "$NVME_SIZE_GB" =~ ^[0-9]+$ ]] || die "NVMe size must be an integer number of GB"
(( NVME_SIZE_GB >= 64 && NVME_SIZE_GB <= 2048 )) || die "NVMe size must be 64..2048 GB"
[[ -x "$L4T_DIR/tools/kernel_flash/l4t_initrd_flash.sh" ]] || die "invalid --l4t-dir"
[[ -s "$L4T_DIR/rootfs/etc/juxin-orin/image.env" ]] || die "Juxin image configuration is missing"
[[ -s "$L4T_DIR/rootfs/usr/lib/juxin-orin/orin-firstboot.sh" ]] || die "first-boot provisioner is missing"
grep -qx 'L4T_BASE=36.4.7' "$L4T_DIR/rootfs/etc/juxin-orin/image-release" \
  || die "rootfs is not the required L4T 36.4.7 image"
[[ ! -e "$L4T_DIR/rootfs/usr/bin/gnome-shell" ]] || die "refusing to flash a desktop rootfs"

recovery_count="$(lsusb -d 0955: 2>/dev/null | wc -l | tr -d ' ')"
[[ "$recovery_count" == "1" ]] || die "expected exactly one NVIDIA Recovery device (0955), found $recovery_count"

if [[ "$ASSUME_YES" != "1" ]]; then
  echo "This will erase the connected Orin Nano NVMe and update its QSPI firmware."
  read -r -p "Type FLASH-ORIN to continue: " confirmation
  [[ "$confirmation" == "FLASH-ORIN" ]] || die "cancelled"
fi

cd "$L4T_DIR"
partition_template="./tools/kernel_flash/flash_l4t_t234_nvme.xml"
partition_config="$(mktemp ./tools/kernel_flash/juxin-orin-nvme.XXXXXX.xml)"
trap 'rm -f "$partition_config"' EXIT
cp "$partition_template" "$partition_config"

# NVMe vendors state capacity in decimal GB. Leave 8 GiB outside APP for the
# other GPT partitions and filesystem overhead.
nvme_sectors="$((NVME_SIZE_GB * 1000000000 / 512))"
nvme_size_gib="$((NVME_SIZE_GB * 1000000000 / 1073741824))"
rootfs_size_gib="$((nvme_size_gib - 8))"
sed -i -E "0,/num_sectors=\"[0-9]+\"/s//num_sectors=\"${nvme_sectors}\"/" "$partition_config"
grep -q "num_sectors=\"${nvme_sectors}\"" "$partition_config" \
  || die "failed to size NVMe partition layout"

./tools/kernel_flash/l4t_initrd_flash.sh \
  --external-device nvme0n1p1 \
  -p "-c ./bootloader/generic/cfg/flash_t234_qspi.xml" \
  -c "$partition_config" \
  -S "${rootfs_size_gib}GiB" \
  --showlogs \
  --network usb0 \
  jetson-orin-nano-devkit-super-maxn external

echo "Flash completed. The node will provision its identity and enroll after DHCP is available."
