# Juxin Orin Image Builder 0.5.1-orin

This package contains the image builder, edge Agent, fullscreen display,
tests and flash scripts. It contains no backend, mini-program, secret or
device-identity data.

## Update an existing Ubuntu build tree

Copy this package to the Ubuntu build host, extract it over the builder
workspace, then reinstall runtime packages and reinject the rootfs:

```bash
tar -xzf juxin-orin-image-builder-0.5.1-orin-3417b67.tar.gz
cd juxin-orin-image-builder-0.5.1-orin-3417b67

./image/tests/run.sh

sudo ./image/build/install-jetpack-runtime.sh \
  --rootfs /srv/nvidia/orin-l4t-36.4.7-v1/Linux_for_Tegra/rootfs

sudo ./image/build/inject-rootfs.sh \
  --rootfs /srv/nvidia/orin-l4t-36.4.7-v1/Linux_for_Tegra/rootfs \
  --ssh-public-key "$HOME/juxin-secrets/orin-maintenance.pub" \
  --api-url https://nvidia.juxinsuanli.cn \
  --image-version orin-l4t-36.4.7-v1 \
  --agent-version 0.5.0-orin \
  --prompt-maintenance-password \
  --quiet-boot
```

The runtime installer now preinstalls `curl`, `wget`, OpenSSH client/server,
`sudo`, Python 3, CA certificates and standard network/process diagnostics.

Before flashing, verify the backend and connect exactly one recovery device:

```bash
./image/tests/check-backend-readiness.sh https://nvidia.juxinsuanli.cn
lsusb -d 0955:7523

sudo ./image/build/flash-nvme.sh \
  --l4t-dir /srv/nvidia/orin-l4t-36.4.7-v1/Linux_for_Tegra \
  --nvme-size-gb 256 \
  --board-config jetson-orin-nano-devkit-super
```

Type `FLASH-ORIN` exactly when prompted. Read `image/README.md` before use.
