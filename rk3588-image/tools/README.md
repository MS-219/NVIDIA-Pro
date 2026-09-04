# RK3588S 工具

## 导出原厂启动分区（Windows）

这个 `.ps1` 可以单独复制到 Windows 桌面运行，不需要复制整个仓库。设备连接并确认
`adb devices` 显示为 `device` 后，在 PowerShell 中运行：

```powershell
cd $env:USERPROFILE\Desktop
powershell -ExecutionPolicy Bypass -File .\backup-rk3588-boot.ps1
```

备份目录默认创建在脚本所在目录；也可以显式指定输出目录：

```powershell
powershell -ExecutionPolicy Bypass -File .\backup-rk3588-boot.ps1 `
  -OutputDir C:\Users\123\Desktop
```

脚本使用原始字节流只读导出 `uboot`、`misc`、`boot`、`recovery`、`backup` 和 `oem`，同时保存设备树型号、
内核命令行、分区列表和厂商启动脚本。它不会写入设备，也不会读取 100GB 的 `userdata`。

请使用修订后的脚本重新备份。旧版 Windows PowerShell 5.1 的 `>` 重定向会把二进制
分区转成文本，若备份中的 `.img` 文件被识别为 UTF-16/UTF-32 或大小约为分区表的 4 倍，
该备份无效，不能用于恢复或刷机。

备份目录中的镜像和哈希是制作可恢复 RootFS/刷机包的基础；在确认备份完整前，不要执行
任何 `rkdeveloptool wl`、`fastboot flash` 或 `dd of=` 命令。
