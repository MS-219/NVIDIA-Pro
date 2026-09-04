# RK3588S 工具

## 导出原厂启动分区（Windows）

设备连接并确认 `adb devices` 显示为 `device` 后，在 PowerShell 中运行：

```powershell
cd C:\path\to\NVIDIA-Pro
powershell -ExecutionPolicy Bypass -File .\rk3588-image\tools\backup-rk3588-boot.ps1
```

脚本只读导出 `uboot`、`misc`、`boot`、`recovery`、`backup` 和 `oem`，同时保存设备树型号、
内核命令行、分区列表和厂商启动脚本。它不会写入设备，也不会读取 100GB 的 `userdata`。

备份目录中的镜像和哈希是制作可恢复 RootFS/刷机包的基础；在确认备份完整前，不要执行
任何 `rkdeveloptool wl`、`fastboot flash` 或 `dd of=` 命令。

