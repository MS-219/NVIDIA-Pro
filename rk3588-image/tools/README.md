# RK3588S 工具

## 导出原厂启动分区（Windows）

设备连接并确认 `adb devices` 显示为 `device` 后，在 PowerShell 中运行：

```powershell
cd C:\path\to\NVIDIA-Pro
powershell -ExecutionPolicy Bypass -File .\rk3588-image\tools\backup-rk3588-boot.ps1
```

脚本使用原始字节流只读导出 `uboot`、`misc`、`boot`、`recovery`、`backup` 和 `oem`，同时保存设备树型号、
内核命令行、分区列表和厂商启动脚本。它不会写入设备，也不会读取 100GB 的 `userdata`。

请使用修订后的脚本重新备份。旧版 Windows PowerShell 5.1 的 `>` 重定向会把二进制
分区转成文本，若备份中的 `.img` 文件被识别为 UTF-16/UTF-32 或大小约为分区表的 4 倍，
该备份无效，不能用于恢复或刷机。

备份目录中的镜像和哈希是制作可恢复 RootFS/刷机包的基础；在确认备份完整前，不要执行
任何 `rkdeveloptool wl`、`fastboot flash` 或 `dd of=` 命令。
