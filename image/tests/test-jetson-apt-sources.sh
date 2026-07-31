#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

mkdir -p "$TEMP_ROOT/etc/apt/sources.list.d"
cat >"$TEMP_ROOT/etc/os-release" <<'EOF'
ID=ubuntu
VERSION_CODENAME=jammy
EOF
cat >"$TEMP_ROOT/etc/apt/sources.list.d/nvidia-l4t-apt-source.list" <<'EOF'
deb https://repo.download.nvidia.com/jetson/common r36.4 main
deb https://repo.download.nvidia.com/jetson/<SOC> r36.4 main
EOF

"$IMAGE_DIR/build/configure-jetson-apt-sources.sh" \
  --rootfs "$TEMP_ROOT" \
  --soc t234

grep -qx 'deb https://repo.download.nvidia.com/jetson/common r36.4 main' \
  "$TEMP_ROOT/etc/apt/sources.list.d/nvidia-l4t-apt-source.list"
grep -qx 'deb https://repo.download.nvidia.com/jetson/t234 r36.4 main' \
  "$TEMP_ROOT/etc/apt/sources.list.d/nvidia-l4t-apt-source.list"
! grep -RqsF 'repo.download.nvidia.com/jetson/<SOC>' "$TEMP_ROOT/etc/apt"

# Re-running the configuration must be safe after a partially failed build.
"$IMAGE_DIR/build/configure-jetson-apt-sources.sh" \
  --rootfs "$TEMP_ROOT" \
  --soc t234

echo "Jetson APT source test: PASS"
