#!/usr/bin/env python3
"""Small, dependency-free Orin node agent.

The image-specific inference runtime is intentionally injected separately. This
agent owns identity, telemetry, task polling and the existing command contract.
"""

from __future__ import annotations

import json
import os
import platform
import re
import socket
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path


API_BASE = os.getenv("ORIN_API_BASE_URL", "http://127.0.0.1:8090").rstrip("/")
SN = os.getenv("ORIN_DEVICE_SN", socket.gethostname())
AGENT_VERSION = os.getenv("ORIN_AGENT_VERSION", "0.1.0-orin")
INTERVAL = max(10, int(os.getenv("ORIN_HEARTBEAT_INTERVAL", "60")))


def request(path: str, method: str = "GET", payload: dict | None = None) -> dict:
    body = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(
        f"{API_BASE}{path}",
        data=body,
        method=method,
        headers={"Content-Type": "application/json", "User-Agent": f"juxin-orin-agent/{AGENT_VERSION}"},
    )
    with urllib.request.urlopen(req, timeout=20) as response:
        return json.loads(response.read().decode())


def read_memory() -> tuple[float, int]:
    values: dict[str, int] = {}
    try:
        for line in Path("/proc/meminfo").read_text().splitlines():
            key, value = line.split(":", 1)
            values[key] = int(value.strip().split()[0])
        total = values.get("MemTotal", 0)
        available = values.get("MemAvailable", values.get("MemFree", 0))
        used = max(0, total - available)
        return (used / total * 100 if total else 0.0, total // 1024)
    except (OSError, ValueError):
        return 0.0, 0


def read_cpu() -> float:
    try:
        first = Path("/proc/stat").read_text().splitlines()[0].split()[1:]
        values = [int(value) for value in first]
        idle = values[3] + values[4]
        total = sum(values)
        time.sleep(0.15)
        second = Path("/proc/stat").read_text().splitlines()[0].split()[1:]
        values2 = [int(value) for value in second]
        idle2 = values2[3] + values2[4]
        total2 = sum(values2)
        delta_total = total2 - total
        return round((1 - (idle2 - idle) / delta_total) * 100, 1) if delta_total else 0.0
    except (OSError, IndexError, ValueError, ZeroDivisionError):
        return 0.0


def read_text(path: str) -> str:
    try:
        return Path(path).read_text(errors="ignore").replace("\x00", "").strip()
    except OSError:
        return ""


def cuda_version() -> str:
    try:
        output = subprocess.check_output(["nvidia-smi"], text=True, stderr=subprocess.DEVNULL, timeout=5)
        match = re.search(r"CUDA Version:\s*([0-9.]+)", output)
        return match.group(1) if match else ""
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
        return ""


def tegra_metrics() -> dict[str, float]:
    try:
        output = subprocess.check_output(["timeout", "2", "tegrastats", "--interval", "1000"], text=True, stderr=subprocess.DEVNULL)
    except (OSError, subprocess.CalledProcessError):
        return {}
    temp = re.search(r"(?:tj|gpu)@([0-9.]+)C", output)
    power = re.search(r"VDD_IN\s+([0-9]+)mW", output)
    gpu = re.search(r"GR3D_FREQ\s+([0-9]+)%", output)
    result = {}
    if temp:
        result["gpu_temperature"] = float(temp.group(1))
    if power:
        result["power_watts"] = float(power.group(1)) / 1000
    if gpu:
        result["gpu_usage"] = float(gpu.group(1))
    return result


def report() -> dict:
    memory, total_memory_mb = read_memory()
    tegra = tegra_metrics()
    model = read_text("/proc/device-tree/model")
    l4t = read_text("/etc/nv_tegra_release").splitlines()
    payload = {
        "sn": SN,
        "cpu_load": f"{read_cpu():.1f}",
        "mem_load": f"{memory:.1f}",
        "cpu_model": platform.machine() + " / " + platform.processor(),
        "agent_version": AGENT_VERSION,
        "image_version": os.getenv("ORIN_IMAGE_VERSION", "orin-l4t-36.4.x"),
        "hardware_fingerprint": os.getenv("ORIN_HARDWARE_FINGERPRINT", ""),
        "ip": socket.gethostbyname(socket.gethostname()),
        "device_model": model,
        "architecture": platform.machine(),
        "l4t_version": l4t[0] if l4t else "",
        "cuda_version": cuda_version(),
    }
    if total_memory_mb:
        payload["memory_total_mb"] = total_memory_mb
    payload.update(tegra)
    return request("/api/edge/report", "POST", payload)


def submit_command(command: dict) -> None:
    command_text = command.get("command") or ""
    result = {"commandNo": command.get("commandNo"), "exitCode": 0, "resultText": ""}
    try:
        completed = subprocess.run(command_text, shell=True, capture_output=True, text=True, timeout=90)
        result["exitCode"] = completed.returncode
        result["resultText"] = (completed.stdout + completed.stderr)[-12000:]
    except subprocess.TimeoutExpired as error:
        result["exitCode"] = 124
        result["resultText"] = f"command timeout: {error}"
    except OSError as error:
        result["exitCode"] = 127
        result["resultText"] = str(error)
    try:
        request("/api/edge/commands/submit", "POST", result)
    except urllib.error.URLError:
        pass


def loop() -> None:
    while True:
        try:
            response = report()
            data = response.get("data") or {}
            if data.get("action") == "execute_command":
                submit_command(data)
        except (OSError, ValueError, urllib.error.URLError) as error:
            print(f"report failed: {error}", flush=True)
        time.sleep(INTERVAL)


if __name__ == "__main__":
    loop()
