#!/usr/bin/env bash
set -Eeuo pipefail

ROOTFS=""
SOC="t234"

usage() {
  echo "Usage: ./configure-jetson-apt-sources.sh --rootfs PATH [--soc t234]"
}

die() {
  echo "configure-jetson-apt-sources: $*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --rootfs) ROOTFS="${2:-}"; shift 2 ;;
    --soc) SOC="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ -n "$ROOTFS" && -f "$ROOTFS/etc/os-release" ]] || die "invalid rootfs"
[[ "$SOC" =~ ^t[0-9]+$ ]] || die "invalid Jetson SoC: $SOC"
ROOTFS="$(realpath "$ROOTFS")"
[[ "$ROOTFS" != "/" ]] || die "refusing to modify the build host root filesystem"

SOURCE_ROOT="$ROOTFS/etc/apt"
[[ -d "$SOURCE_ROOT" ]] || die "rootfs is missing /etc/apt"

found_soc_source=0
while IFS= read -r -d '' source_file; do
  if grep -qF 'repo.download.nvidia.com/jetson/<SOC>' "$source_file"; then
    sed -i.juxin-backup "s#repo.download.nvidia.com/jetson/<SOC>#repo.download.nvidia.com/jetson/${SOC}#g" \
      "$source_file"
    rm -f "$source_file.juxin-backup"
  fi

  if grep -qF "repo.download.nvidia.com/jetson/${SOC}" "$source_file"; then
    found_soc_source=1
  fi
done < <(find -L "$SOURCE_ROOT" -type f \( -name '*.list' -o -name '*.sources' \) -print0)

if grep -RqsF 'repo.download.nvidia.com/jetson/<SOC>' "$SOURCE_ROOT"; then
  die "unresolved <SOC> placeholder remains in the Jetson APT sources"
fi
[[ "$found_soc_source" == "1" ]] \
  || die "Jetson ${SOC} APT source was not found after configuration"

echo "Configured NVIDIA Jetson APT source for ${SOC}"
