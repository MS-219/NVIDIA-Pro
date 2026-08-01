#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

bash -n \
  "$IMAGE_DIR/agent/install-agent.sh" \
  "$IMAGE_DIR/agent/orin-firstboot.sh" \
  "$IMAGE_DIR/agent/orin-performance.sh" \
  "$IMAGE_DIR/build/inject-rootfs.sh" \
  "$IMAGE_DIR/build/configure-jetson-apt-sources.sh" \
  "$IMAGE_DIR/build/select-jetson-board-config.sh" \
  "$IMAGE_DIR/build/install-jetpack-runtime.sh" \
  "$IMAGE_DIR/build/prepare-image.sh" \
  "$IMAGE_DIR/build/flash-nvme.sh" \
  "$IMAGE_DIR/tests/check-backend-readiness.sh" \
  "$IMAGE_DIR/tests/test-board-config.sh" \
  "$IMAGE_DIR/tests/test-jetson-apt-sources.sh" \
  "$IMAGE_DIR/tests/test-firstboot.sh"

grep -q 'L4T_VERSION="36.4.7-20250918154033"' \
  "$IMAGE_DIR/build/install-jetpack-runtime.sh"
grep -q '"$board_config" external' \
  "$IMAGE_DIR/build/flash-nvme.sh"
grep -q -- '--read-info "$probe_board_config" internal' \
  "$IMAGE_DIR/build/flash-nvme.sh"
grep -q 'cd "$L4T_DIR"' "$IMAGE_DIR/build/flash-nvme.sh"
grep -q 'read_info_status="${PIPESTATUS\[0\]}"' \
  "$IMAGE_DIR/build/flash-nvme.sh"
grep -q 'initial_recovery_device=' "$IMAGE_DIR/build/flash-nvme.sh"
grep -q 'reenumerated_recovery_device=' "$IMAGE_DIR/build/flash-nvme.sh"
grep -q 'EEPROM probe skipped' "$IMAGE_DIR/build/flash-nvme.sh"
grep -q 'rm -f "$L4T_DIR/bootloader/cvm.bin"' "$IMAGE_DIR/build/flash-nvme.sh"
grep -q 'USB write failed while reading EEPROM' "$IMAGE_DIR/build/flash-nvme.sh"
grep -qF 'num_sectors=\"[^\"]+\"' "$IMAGE_DIR/build/flash-nvme.sh"
grep -q 'nvidia-ctk runtime configure --runtime=docker' \
  "$IMAGE_DIR/build/install-jetpack-runtime.sh"
grep -qF '.nv-l4t-disable-boot-fw-update-in-preinstall' \
  "$IMAGE_DIR/build/install-jetpack-runtime.sh"
grep -q 'apt-get --fix-broken install -y' \
  "$IMAGE_DIR/build/install-jetpack-runtime.sh"
[[ "$(grep -c '/usr/bin/dpkg-query' \
  "$IMAGE_DIR/build/install-jetpack-runtime.sh")" == "2" ]]
grep -q 'fonts-noto-cjk' "$IMAGE_DIR/build/install-jetpack-runtime.sh"
grep -q 'python3-pil' "$IMAGE_DIR/build/install-jetpack-runtime.sh"
grep -q 'python3-websocket' "$IMAGE_DIR/build/install-jetpack-runtime.sh"
grep -q 'iputils-ping' "$IMAGE_DIR/build/install-jetpack-runtime.sh"
grep -q 'python3-websocket is not installed in rootfs' \
  "$IMAGE_DIR/build/inject-rootfs.sh"
grep -q 'iputils-ping is not installed in rootfs' \
  "$IMAGE_DIR/build/inject-rootfs.sh"
grep -q 'bootloader/system.img.raw' "$IMAGE_DIR/build/flash-nvme.sh"
grep -q 'rootfs contains device-specific state' "$IMAGE_DIR/build/flash-nvme.sh"
grep -q 'juxin-orin-display.service' "$IMAGE_DIR/build/inject-rootfs.sh"
grep -q 'orin-core.png' "$IMAGE_DIR/build/inject-rootfs.sh"
grep -q 'orin-core.png' "$IMAGE_DIR/agent/install-agent.sh"
test -s "$IMAGE_DIR/agent/orin-core.png"
grep -q 'getty@tty1.service' "$IMAGE_DIR/build/inject-rootfs.sh"
grep -q -- '--prompt-maintenance-password' "$IMAGE_DIR/build/inject-rootfs.sh"
grep -q 'maintenance password must contain at least 8 characters' \
  "$IMAGE_DIR/build/inject-rootfs.sh"
grep -q 'openssl passwd -6 -stdin' "$IMAGE_DIR/build/inject-rootfs.sh"
grep -q 'usermod --root' "$IMAGE_DIR/build/inject-rootfs.sh"
grep -q 'inject_args+=(--prompt-maintenance-password)' "$IMAGE_DIR/build/prepare-image.sh"

python3 -m py_compile \
  "$IMAGE_DIR/agent/orin_agent.py" \
  "$IMAGE_DIR/agent/orin_display.py"
python3 -m unittest discover -v -s "$IMAGE_DIR/tests" -p 'test_*.py'
"$IMAGE_DIR/tests/test-board-config.sh"
"$IMAGE_DIR/tests/test-jetson-apt-sources.sh"
"$IMAGE_DIR/tests/test-firstboot.sh"

echo "image tests: PASS"
