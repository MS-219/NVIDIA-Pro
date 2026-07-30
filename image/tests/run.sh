#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

bash -n \
  "$IMAGE_DIR/agent/install-agent.sh" \
  "$IMAGE_DIR/agent/orin-firstboot.sh" \
  "$IMAGE_DIR/agent/orin-performance.sh" \
  "$IMAGE_DIR/build/inject-rootfs.sh" \
  "$IMAGE_DIR/build/install-jetpack-runtime.sh" \
  "$IMAGE_DIR/build/prepare-image.sh" \
  "$IMAGE_DIR/build/flash-nvme.sh" \
  "$IMAGE_DIR/tests/check-backend-readiness.sh" \
  "$IMAGE_DIR/tests/test-firstboot.sh"

grep -q 'L4T_VERSION="36.4.7-20250918154033"' \
  "$IMAGE_DIR/build/install-jetpack-runtime.sh"
grep -q 'jetson-orin-nano-devkit-super-maxn external' \
  "$IMAGE_DIR/build/flash-nvme.sh"
grep -q 'nvidia-ctk runtime configure --runtime=docker' \
  "$IMAGE_DIR/build/install-jetpack-runtime.sh"

python3 -m py_compile "$IMAGE_DIR/agent/orin_agent.py"
python3 -m unittest discover -v -s "$IMAGE_DIR/tests" -p 'test_*.py'
"$IMAGE_DIR/tests/test-firstboot.sh"

echo "image tests: PASS"
