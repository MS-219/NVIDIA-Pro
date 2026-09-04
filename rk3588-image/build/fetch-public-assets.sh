#!/usr/bin/env bash
set -Eeuo pipefail

out_dir="${1:-vendor-public}"
mkdir -p "$out_dir"

dts_api="https://api.github.com/repos/iwurui/Touchfly-CX3588/contents/rk3588s-touchtec.dts?ref=main"
dtb_api="https://api.github.com/repos/iwurui/Touchfly-CX3588/contents/rk3588s-touchtec.dtb_old?ref=f88e797268281db9b68efc5b48002af52ca4b531"
repo_url="https://github.com/iwurui/Touchfly-CX3588"

download_github_content() {
  local url="$1"
  local target="$2"
  local response
  response="$(curl --fail --location --retry 3 --connect-timeout 10 --max-time 120 \
    --silent --show-error "$url")"
  python3 -c 'import base64, json, sys; data=json.loads(sys.stdin.read()); raw=data.get("content", "").replace("\\n", ""); assert raw, "GitHub API returned no content"; open(sys.argv[1], "wb").write(base64.b64decode(raw))' \
    "$target" <<<"$response"
  test -s "$target"
}

download_github_content "$dts_api" "$out_dir/rk3588s-touchtec.dts"
download_github_content "$dtb_api" "$out_dir/rk3588s-touchtec.dtb"

grep -q 'Rockchip RK3588S EVB4 LP4X V10 Board' "$out_dir/rk3588s-touchtec.dts"

if command -v file >/dev/null 2>&1; then
  file "$out_dir/rk3588s-touchtec.dts" "$out_dir/rk3588s-touchtec.dtb"
fi

sha256sum "$out_dir/rk3588s-touchtec.dts" "$out_dir/rk3588s-touchtec.dtb" \
  | tee "$out_dir/SHA256SUMS"
printf '来源：%s\n' "$repo_url" > "$out_dir/SOURCE.txt"
printf '公开 DTS/DTB 已下载到 %s；它们不是可直接刷写的完整固件。\n' "$out_dir"
