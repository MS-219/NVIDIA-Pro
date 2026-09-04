# CX3588-A 硬件描述

`fetch-public-assets.sh` 会下载公开的设备树源码和 DTB：

- `rk3588s-touchtec.dts`：从 Ubuntu 22.04 系统提取的设备树源码；
- `rk3588s-touchtec.dtb`：同一仓库历史提交中的设备树二进制。

两者用于校对屏幕、触摸、eMMC、以太网、Wi-Fi、GPIO 和 NPU 配置，不包含 U-Boot、
DDR 初始化程序或完整 RootFS。构建正式刷机包前，必须把它们与厂商 BSP 的内核版本
和板卡修订号进行比对。

