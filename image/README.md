# Juxin Orin Node Image

This directory builds the independent Juxin Orin compute-node image for the
Jetson Orin Nano Super 8GB. NVIDIA publishes R36.4.7 through the `r36.4` APT
repository rather than as a standalone BSP archive. The build therefore starts
from the official R36.4.4 BSP, generates NVIDIA's `basic` Ubuntu 22.04 rootfs,
then upgrades all L4T packages to R36.4.7.

The image retains NVIDIA UEFI, the native NVIDIA boot logo, Jetson drivers and
the JetPack 6.2.1 runtime, Docker and NVIDIA Container Toolkit. It does not contain the admin console, mini program,
old LD-AI agent, models, user data or any per-device identity.

## Boot and enrollment lifecycle

```text
NVIDIA UEFI logo
  -> Ubuntu multi-user target
  -> juxin-orin-firstboot.service
  -> derive ORIN-xxxxxxxxxxxxxxxx from the immutable Tegra serial
     (physical MAC fallback only when no hardware serial is available)
  -> generate machine-id and SSH host keys
  -> enroll with the device SN and hardware fingerprint
  -> persist the unique Orin-A1B2C3 binding code and per-device token
  -> juxin-orin-display.service takes over tty1
  -> juxin-orin-agent.service heartbeat and task polling
  -> device appears online at nvidia.juxinsuanli.cn
```

On first boot, the node enrolls directly with its generated SN and hardware
fingerprint. The backend returns a database-unique short binding code and a
device token, while storing only the token hash. All later edge requests use
the full SN and token; the display and user binding flow use the short code.
The remote terminal uses the same device token for its outbound WebSocket and a
30-second, one-time admin ticket. Opening a session starts the `juxin`
maintenance shell rather than a root shell.

## Image contract

- Architecture: `aarch64`
- BSP bootstrap: NVIDIA Jetson Linux R36.4.4
- Installed L4T packages: `36.4.7-20250918154033`
- JetPack runtime: `nvidia-jetpack-runtime=6.2.1+b38`
- Rootfs: Ubuntu 22.04 `basic`, no GNOME or display manager
- Node directory: `/opt/juxin-orin`
- Model directory: `/opt/juxin-orin/models`
- State directory: `/var/lib/juxin-orin`
- Display status: `/var/lib/juxin-orin/display-status.json`
- Result outbox: `/var/lib/juxin-orin/outbox`
- Remote terminal: authenticated outbound WebSocket with an on-demand `juxin` PTY
- Configuration: `/etc/juxin-orin`
- Performance: backend-managed `15W`, `25W`, or `MAXN_SUPER` mode with the `cool` fan profile
- Containers: Docker with NVIDIA Container Toolkit
- Maintenance tools: `curl`, `wget`, OpenSSH client/server, `sudo`, `iproute2`, `procps`, `util-linux`, CA certificates and Python 3
- Services: `juxin-orin-firstboot.service`, `juxin-orin-performance.service`, `juxin-orin-display.service`, `juxin-orin-agent.service`
- Network for first enrollment: wired DHCP

The built-in compute handler accepts `ollama` tasks and calls the local Ollama
API. Other task types require an executable `/opt/juxin-orin/runtime/task-runner`
that accepts the task JSON on standard input and returns result JSON on standard
output. Without that explicit runner, unknown task types are reported as failed
and are never executed as shell commands. Command and task results are written
to the local outbox before delivery, then retried after transient network or
server errors.

The fullscreen status display renders directly to the Linux framebuffer and does
not require GNOME, X11, Wayland or a browser. It shows the node SN, connection
state, animated compute field and live telemetry. During a real task it switches
to the task state and may show the task type, model and elapsed time; prompts,
tokens and command contents are never written to the display status file. `tty1`
is reserved for the display. Use SSH for routine maintenance or `Ctrl+Alt+F2`
for a local text console.

Do not use a generic Ubuntu ARM64 ISO or the old R35.3.1 package. Do not clone a
configured device with `dd`: that duplicates secrets, machine IDs and SSH keys.

## 1. Prepare the build host

Use a physical x86_64 Ubuntu 22.04 host with an ext4, XFS or Btrfs work disk,
at least 100 GB free, and preferably 16 GB RAM. Apple Silicon macOS is suitable
for source work and downloads, but not for the NVIDIA flash toolchain.

Copy both verified downloads to the Ubuntu host. The Sample RootFS is checked
but is not installed because it contains the graphical desktop:

```text
Jetson_Linux_R36.4.4_aarch64.tbz2
Tegra_Linux_Sample-Root-Filesystem_R36.4.4_aarch64.tbz2
```

Expected SHA-1:

```text
1039c377717e443cbabd9a1a719162dd84ab4678  Jetson_Linux_R36.4.4_aarch64.tbz2
73df3f66ad77f29d1424d61dbb45d5587090c912  Tegra_Linux_Sample-Root-Filesystem_R36.4.4_aarch64.tbz2
```

The downloaded desktop Sample RootFS may be retained as an official reference,
but the product build intentionally generates NVIDIA's Basic rootfs instead.

## 2. Prepare maintenance SSH access

The image creates a `juxin` maintenance account. Root and SSH password login stay
disabled; an SSH public key is required:

```bash
ssh-keygen -t ed25519 -f ~/juxin-secrets/orin-maintenance -C juxin-orin-maintenance
```

Never put the private key in the repository or image.

## 3. Build the non-GUI rootfs

From this repository on the Ubuntu build host:

```bash
sudo ./image/build/prepare-image.sh \
  --downloads /srv/nvidia/downloads \
  --work-dir /srv/nvidia/orin-l4t-36.4.7-v1 \
  --ssh-public-key /home/BUILD_USER/juxin-secrets/orin-maintenance.pub \
  --image-version orin-l4t-36.4.7-v1 \
  --prompt-maintenance-password
```

The password prompt requires at least eight characters and stores only a
SHA-512 hash in the rootfs. Every device flashed from that prepared rootfs uses
the same password for local `sudo`; SSH continues to require the private key.
Omit `--prompt-maintenance-password` to keep the maintenance password locked.

The script performs these steps:

1. verifies the official BSP checksum;
2. extracts `Linux_for_Tegra` on a Linux-native filesystem;
3. generates NVIDIA's Basic Ubuntu 22.04 sample filesystem;
4. applies NVIDIA BSP binaries;
5. upgrades L4T packages to R36.4.7 and installs the JetPack 6.2.1 runtime;
6. installs Docker and configures NVIDIA Container Toolkit;
7. installs the CJK framebuffer renderer, fonts and remote maintenance/download tools;
8. injects the agent, backend-managed power/cooling policy, wired DHCP and maintenance SSH key;
9. clears machine-id, SSH host keys, device identity, display state and device token.

The default variant shows the NVIDIA logo, normal Linux boot output and then the
fullscreen Juxin node display. Add `--quiet-boot` to suppress the Linux boot
messages between the NVIDIA logo and the node display.

The output is a prepared `Linux_for_Tegra` tree. No device-specific state is
created until the flashed Orin boots.

## 4. Verify the deployed backend

Do not flash a node until the backend release and public Agent assets have been
deployed. From the same source revision that will build the image, run this
read-only gate:

```bash
./image/tests/check-backend-readiness.sh https://nvidia.juxinsuanli.cn
```

The gate requires the application and database to be healthy, edge protocol
version `2`, minimum Agent version `0.5.1-orin`, and byte-for-byte matches for
the five public Agent/display upgrade files. It does not enroll a device, claim
a task, or write any production data.

## 5. Flash one pilot node

Connect one Orin Nano to the Ubuntu host using its USB-C recovery port, enter
Force Recovery mode, and verify that `lsusb` shows exactly one NVIDIA `0955`
device. Then run:

```bash
sudo ./image/build/flash-nvme.sh \
  --l4t-dir /srv/nvidia/orin-l4t-36.4.7-v1/Linux_for_Tegra \
  --nvme-size-gb 256
```

The script detects the module EEPROM, selects the matching NVIDIA board config,
requires the operator to type `FLASH-ORIN`, and then flashes QSPI and the 256 GB
NVMe using NVIDIA `l4t_initrd_flash.sh`. It refuses a desktop rootfs, a
non-R36.4.7 rootfs or a missing first-boot configuration. For a module whose
config is already known, pass `--board-config CONFIG` to skip the extra EEPROM
RCM transaction; this is useful when recovering a partially flashed device.

## 6. Verify the first boot

Connect wired Ethernet before powering on the node. On the node or through SSH:

```bash
cat /var/lib/juxin-orin/device-sn
cat /var/lib/juxin-orin/bind-code
cat /etc/juxin-orin/image-release
systemctl status juxin-orin-firstboot.service --no-pager
systemctl status juxin-orin-performance.service --no-pager
systemctl status juxin-orin-display.service --no-pager
systemctl status juxin-orin-agent.service --no-pager
journalctl -u juxin-orin-display.service -n 100 --no-pager
journalctl -u juxin-orin-agent.service -n 100 --no-pager
nvpmodel -q
nvfancontrol -q
docker info
```

The expected result is one `ORIN-...` device in the admin console with L4T,
CUDA, GPU, memory, temperature and power telemetry. A device token must exist at
`/var/lib/juxin-orin/device-token` with mode `0600`; never print its value. The
connected display should show the `Orin-XXXXXX` binding code and transition
from initialization to the online compute animation.

## Tests

Tests run without an Orin or Linux rootfs:

```bash
./image/tests/run.sh
```

Actual RootFS construction and flashing can only be verified on the Ubuntu
build host and one physical pilot device.
