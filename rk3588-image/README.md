# 聚芯节点 RK3588S 镜像工程

这是与原 Jetson Orin `image/` 完全分离的 RK3588S 镜像工程。它面向
Touchfly/触想 CX3588-A（设备树型号 `Rockchip RK3588S EVB4 LP4X V10 Board`），
目标是在保留瑞芯微/板卡厂商启动链和启动 Logo 的前提下，重新构建聚芯节点的
Linux 系统、全屏 UI、设备 Agent 和后台接入。

## 当前状态

- 已确认硬件型号：RK3588S、LP4X、aarch64、Buildroot 2021.11、eMMC 启动。
- 已找到公开的 CX3588-A DTS/DTB，见 `build/fetch-public-assets.sh`。
- 已加入独立的 RK3588S Agent、全屏 UI、首次启动身份初始化脚本，见 `agent/`。
- 已加入 Windows 只读导出原厂启动分区工具，见 `tools/backup-rk3588-boot.ps1`。
- 尚未找到公开的 CX3588-A 专用 U-Boot/DDR Loader/完整 `update.img`。
- 因此当前工程默认采用“保留原厂启动链，替换 Linux RootFS”的安全路线。
- 在拿到匹配 BSP 前，工程不会执行 `dd`、`rkdeveloptool wl` 或整盘刷写。

## 产品启动链

```text
瑞芯微/板卡厂商 U-Boot Logo
  -> RK3588S Linux/Buildroot
  -> 聚芯节点 init 脚本
  -> 聚芯节点全屏 UI
  -> RK3588S Agent（设备入网、心跳、任务）
  -> jd.ldjuxin.yun 设备后台
```

旧 Jetson 镜像中的 NVIDIA、L4T、CUDA、`nvpmodel` 和 `tegrastats` 不会复制到本工程。
UI 保持聚芯节点的产品布局和交互，但硬件状态采集改为 RK3588S 的 CPU、内存、Mali、
NPU、温度和网络指标。

设备 Agent 默认连接独立 APP 后台 `https://jd.ldjuxin.yun`，使用 `/api/edge/enroll`、
`/api/edge/report`、`/api/edge/tasks/fetch` 和 `/api/edge/tasks/submit`。这些设备接口
已加入 `app-backend`，与 APP 的短信登录和用户绑定共用同一域名。

## 公开硬件资料

- 官方产品页：<https://www.touchflyboard.com/touchfly-cx3588-a-rk3588-android-13-8-core-64-bit-with-npu-6tops-ai-8k-mc4-gpu-1000-gigabit-hdmi-lvds>
- 规格 PDF：<https://www.touchflyboard.com/wp-content/uploads/2024/04/CX3588-A.pdf>
- DTS/DTB 仓库：<https://github.com/iwurui/Touchfly-CX3588>
- 相关支持请求：<https://github.com/ophub/fnnas/issues/411>

下载公开硬件描述（不会写入设备）：

```bash
./rk3588-image/build/fetch-public-assets.sh ./rk3588-image/vendor-public
```

在已具备 Python/Pillow 的临时 RootFS 中，可以先做离线冒烟测试：

```bash
python3 -m py_compile rk3588-image/agent/rk3588_agent.py \
  rk3588-image/agent/rk3588_display.py
./rk3588-image/build/verify-layout.sh
```

`agent/S99juxin-rk3588` 是 Buildroot `/etc/init.d/` 风格的启动模板；正式注入时，
需要把 Agent、UI 和 `rk3588-core.png` 安装到 `/opt/juxin-rk3588/`。设备原厂 RootFS
未预装 Python3 时，可注入匹配 glibc/aarch64 的独立 Python 运行时；Pillow、字体和
framebuffer/DRM 输出链路仍需在测试板上确认。

## 厂商 BSP 到位后的构建顺序

1. 将厂商 BSP 解压到独立的 Linux 构建主机，并在 `vendor-bsp/` 中配置路径。
2. 先运行 `tools/backup-rk3588-boot.ps1` 导出原厂启动分区，保留可恢复副本。
3. 用原厂 Loader、U-Boot、分区表和设备树生成可启动的 RK 镜像。
4. 将 `rootfs/` 中的聚芯节点 UI、Agent、后台地址和 init 脚本注入 RootFS。
5. 在一块测试板上先通过 ADB/串口验证显示、触摸、网络和 NPU，再制作刷机包。

## 目前不能做的事情

没有与 CX3588-A 修订版匹配的 DDR Loader、分区参数和恢复包时，不能把其他 RK3588
开发板的 `update.img` 当作本板固件。即使设备树名称相同，DDR、电源时序、屏幕和
eMMC 参数也可能不同。
