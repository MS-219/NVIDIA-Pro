#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
L4T_DIR=""
ASSUME_YES=0
NVME_SIZE_GB=256
BOARD_CONFIG_OVERRIDE=""
READ_INFO_LOG=""
PARTITION_CONFIG=""

usage() {
  echo "Usage: sudo ./flash-nvme.sh --l4t-dir PATH [--nvme-size-gb 256] [--board-config CONFIG] [--yes]"
}

die() {
  echo "flash-nvme: $*" >&2
  exit 1
}

cleanup() {
  [[ -z "$READ_INFO_LOG" ]] || rm -f "$READ_INFO_LOG"
  [[ -z "$PARTITION_CONFIG" ]] || rm -f "$PARTITION_CONFIG"
}
trap cleanup EXIT

while [[ $# -gt 0 ]]; do
  case "$1" in
    --l4t-dir) L4T_DIR="${2:-}"; shift 2 ;;
    --nvme-size-gb) NVME_SIZE_GB="${2:-}"; shift 2 ;;
    --board-config) BOARD_CONFIG_OVERRIDE="${2:-}"; shift 2 ;;
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
L4T_DIR="$(realpath "$L4T_DIR")"
[[ -s "$L4T_DIR/rootfs/etc/juxin-orin/image.env" ]] || die "Juxin image configuration is missing"
[[ -s "$L4T_DIR/rootfs/usr/lib/juxin-orin/orin-firstboot.sh" ]] || die "first-boot provisioner is missing"
grep -qx 'L4T_BASE=36.4.7' "$L4T_DIR/rootfs/etc/juxin-orin/image-release" \
  || die "rootfs is not the required L4T 36.4.7 image"
[[ ! -e "$L4T_DIR/rootfs/usr/bin/gnome-shell" ]] || die "refusing to flash a desktop rootfs"
for state_file in \
  device-sn hardware-fingerprint bind-code device-token display-status.json device-seed provisioned; do
  [[ ! -e "$L4T_DIR/rootfs/var/lib/juxin-orin/$state_file" ]] \
    || die "rootfs contains device-specific state ($state_file); rerun inject-rootfs.sh"
done

recovery_count="$(lsusb -d 0955:7523 2>/dev/null | wc -l | tr -d ' ')"
[[ "$recovery_count" == "1" ]] || die "expected exactly one NVIDIA Recovery device (0955), found $recovery_count"
board_config=""

if [[ -n "$BOARD_CONFIG_OVERRIDE" ]]; then
  board_config="$BOARD_CONFIG_OVERRIDE"
  echo "Using explicit NVIDIA board config: ${board_config} (EEPROM probe skipped)"
else
  initial_recovery_device="$(lsusb -d 0955:7523 2>/dev/null)"
  READ_INFO_LOG="$(mktemp)"
  echo "Detecting the connected Jetson module..."
  probe_board_config="jetson-orin-nano-devkit-super-maxn"
  [[ -e "$L4T_DIR/${probe_board_config}.conf" ]] \
    || die "EEPROM probe board config not found: $probe_board_config"

  # flash.sh writes probe results to fixed paths. Remove the old EEPROM dump so
  # a failed USB transaction cannot be mistaken for a successful fresh probe.
  rm -f "$L4T_DIR/bootloader/cvm.bin"
  set +e
  cd "$L4T_DIR"
  ./flash.sh --read-info "$probe_board_config" internal 2>&1 \
    | tee "$READ_INFO_LOG"
  read_info_status="${PIPESTATUS[0]}"
  set -e

  if grep -qE 'ERROR: might be timeout in USB write|Error: Return value 3' "$READ_INFO_LOG"; then
    die "USB write failed while reading EEPROM; power-cycle the Jetson, enter Recovery mode again, and reconnect APX"
  fi
  [[ -s "$L4T_DIR/bootloader/cvm.bin" ]] \
    || die "EEPROM probe did not produce fresh board data"

  board_line="$(grep 'Board ID(' "$READ_INFO_LOG" | tail -n 1 || true)"
  board_id="$(sed -nE 's/.*Board ID\(([0-9]+)\).*/\1/p' <<<"$board_line")"
  board_sku="$(sed -nE 's/.*sku\(([0-9]+)\).*/\1/p' <<<"$board_line")"
  board_fab="$(sed -nE 's/.*version\(([0-9]+)\).*/\1/p' <<<"$board_line")"
  [[ -n "$board_id" && -n "$board_sku" && -n "$board_fab" ]] \
    || die "failed to parse the Jetson module EEPROM"
  if [[ "$read_info_status" != "0" ]]; then
    echo "EEPROM probe returned status ${read_info_status} after reading board identity; selecting the matching config."
  fi

  board_config="$("$SCRIPT_DIR/select-jetson-board-config.sh" \
    --board-id "$board_id" \
    --board-sku "$board_sku")"
  echo "Detected Jetson module: board ${board_id}, SKU ${board_sku}, FAB ${board_fab}"
  echo "Selected NVIDIA board config: ${board_config}"

  echo "Waiting for the Jetson to re-enumerate in Recovery mode..."
  reenumerated_recovery_device=""
  for _ in {1..100}; do
    current_recovery_device="$(lsusb -d 0955:7523 2>/dev/null || true)"
    if [[ -n "$current_recovery_device" && "$current_recovery_device" != "$initial_recovery_device" ]]; then
      reenumerated_recovery_device="$current_recovery_device"
      break
    fi
    sleep 0.1
  done
  [[ -n "$reenumerated_recovery_device" ]] \
    || die "Jetson did not re-enumerate in Recovery mode; reconnect NVIDIA APX to this host"
  sleep 2
  [[ "$(lsusb -d 0955:7523 2>/dev/null || true)" == "$reenumerated_recovery_device" ]] \
    || die "Jetson Recovery USB connection is not stable"
fi

[[ "$board_config" =~ ^[A-Za-z0-9._+-]+$ ]] || die "invalid board config: $board_config"
[[ -f "$L4T_DIR/${board_config}.conf" || -L "$L4T_DIR/${board_config}.conf" ]] \
  || die "board config not found: $board_config"

if [[ "$ASSUME_YES" != "1" ]]; then
  echo "This will erase the connected Orin Nano NVMe and update its QSPI firmware."
  read -r -p "Type FLASH-ORIN to continue: " confirmation
  [[ "$confirmation" == "FLASH-ORIN" ]] || die "cancelled"
fi

cd "$L4T_DIR"
# L4T reuses an existing sparse image even after rootfs files change. Remove
# only generated system images so every flash contains the current clean rootfs.
rm -f ./bootloader/system.img ./bootloader/system.img.raw
if [[ -d ./tools/kernel_flash/images ]]; then
  find ./tools/kernel_flash/images -type f -name 'system.img*' -delete
fi
partition_template="./tools/kernel_flash/flash_l4t_t234_nvme.xml"
PARTITION_CONFIG="$(mktemp ./tools/kernel_flash/juxin-orin-nvme.XXXXXX.xml)"
cp "$partition_template" "$PARTITION_CONFIG"

# NVMe vendors state capacity in decimal GB. Leave 8 GiB outside APP for the
# other GPT partitions and filesystem overhead.
nvme_sectors="$((NVME_SIZE_GB * 1000000000 / 512))"
nvme_size_gib="$((NVME_SIZE_GB * 1000000000 / 1073741824))"
rootfs_size_gib="$((nvme_size_gib - 8))"
sed -i -E "0,/num_sectors=\"[^\"]+\"/s//num_sectors=\"${nvme_sectors}\"/" "$PARTITION_CONFIG"
grep -q "num_sectors=\"${nvme_sectors}\"" "$PARTITION_CONFIG" \
  || die "failed to size NVMe partition layout"

./tools/kernel_flash/l4t_initrd_flash.sh \
  --external-device nvme0n1p1 \
  -p "-c ./bootloader/generic/cfg/flash_t234_qspi.xml" \
  -c "$PARTITION_CONFIG" \
  -S "${rootfs_size_gib}GiB" \
  --showlogs \
  --network usb0 \
  "$board_config" external

echo "Flash completed. The node will provision its identity and enroll after DHCP is available."
