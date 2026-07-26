# Orin Image

目标设备为 Jetson Orin Nano Super 8GB，当前基线为 L4T `36.4.7`。系统镜像保留 NVIDIA UEFI 和原生开机 Logo，只定制 Ubuntu 用户空间、聚芯节点服务和 GPU 推理运行环境。

## Image contract

- 架构：`aarch64`
- BSP：NVIDIA Jetson Linux/L4T `36.4.x`
- 节点目录：`/opt/juxin-orin`
- 模型目录：`/opt/juxin-orin/models`
- 数据目录：`/var/lib/juxin-orin`
- 日志目录：`/var/log/juxin-orin`
- 服务名：`juxin-orin-agent.service`

不要使用 Ubuntu 官网通用 ARM64 镜像覆盖 Jetson BSP。系统镜像不得包含设备证书、节点 ID、微信密钥或后台凭据，这些信息必须在设备首次启动时生成或下发。

`agent/` 将承载 ARM64 节点 Agent；`output/` 和 `models/` 均被 Git 忽略。
