#!/usr/bin/env python3
"""Dependency-free agent for Juxin Orin compute nodes."""

from __future__ import annotations

import json
import hashlib
import os
import platform
import re
import socket
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


API_BASE = os.getenv("ORIN_API_BASE_URL", "https://nvidia.juxinsuanli.cn").rstrip("/")
AGENT_VERSION = os.getenv("ORIN_AGENT_VERSION", "0.3.0-orin")
IMAGE_VERSION = os.getenv("ORIN_IMAGE_VERSION", "orin-l4t-36.4.7-v1")
IMAGE_LICENSE = os.getenv("ORIN_IMAGE_LICENSE", "").strip()
STATE_DIR = Path(os.getenv("ORIN_STATE_DIR", "/var/lib/juxin-orin"))
SN_FILE = Path(os.getenv("ORIN_DEVICE_SN_FILE", str(STATE_DIR / "device-sn")))
FINGERPRINT_FILE = Path(
    os.getenv("ORIN_HARDWARE_FINGERPRINT_FILE", str(STATE_DIR / "hardware-fingerprint"))
)
TOKEN_FILE = Path(os.getenv("ORIN_DEVICE_TOKEN_FILE", str(STATE_DIR / "device-token")))
DEFAULT_INTERVAL = max(10, int(os.getenv("ORIN_HEARTBEAT_INTERVAL", "60")))
DEFAULT_TASK_POLL_INTERVAL = max(10, int(os.getenv("ORIN_TASK_POLL_INTERVAL", "60")))
COMMAND_TIMEOUT = max(30, int(os.getenv("ORIN_COMMAND_TIMEOUT", "90")))
TASK_TIMEOUT = min(240, max(30, int(os.getenv("ORIN_TASK_TIMEOUT", "240"))))
REQUEST_TIMEOUT = max(5, int(os.getenv("ORIN_REQUEST_TIMEOUT", "20")))
REQUEST_RETRIES = min(5, max(0, int(os.getenv("ORIN_REQUEST_RETRIES", "2"))))
RETRY_BASE_SECONDS = max(0.1, float(os.getenv("ORIN_RETRY_BASE_SECONDS", "1")))
OUTBOX_RETRY_INTERVAL = max(5, int(os.getenv("ORIN_OUTBOX_RETRY_INTERVAL", "15")))
OUTBOX_DIR = Path(os.getenv("ORIN_OUTBOX_DIR", str(STATE_DIR / "outbox")))
RUNTIME_DIR = Path(os.getenv("ORIN_RUNTIME_DIR", "/opt/juxin-orin/runtime"))
TASK_RUNNER = Path(os.getenv("ORIN_TASK_RUNNER", str(RUNTIME_DIR / "task-runner")))
OLLAMA_API_BASE = os.getenv("ORIN_OLLAMA_API_BASE_URL", "http://127.0.0.1:11434").rstrip("/")
MAX_RESULT_TEXT = 1_000_000
MAX_ERROR_TEXT = 4000


class ApiError(RuntimeError):
    """The API returned an HTTP or business-level error."""

    def __init__(self, message: str, *, retryable: bool = False, status: int | None = None):
        super().__init__(message)
        self.retryable = retryable
        self.status = status


def read_text(path: str | Path) -> str:
    try:
        return Path(path).read_text(errors="ignore").replace("\x00", "").strip()
    except OSError:
        return ""


def atomic_write_secret(path: Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            handle.write(value)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass


def device_sn() -> str:
    configured = os.getenv("ORIN_DEVICE_SN", "").strip()
    value = configured or read_text(SN_FILE)
    if not re.fullmatch(r"ORIN-[A-F0-9]{12,32}", value):
        raise RuntimeError("device identity is missing; juxin-orin-firstboot.service must run first")
    return value


def hardware_fingerprint() -> str:
    configured = os.getenv("ORIN_HARDWARE_FINGERPRINT", "").strip()
    value = configured or read_text(FINGERPRINT_FILE)
    if not re.fullmatch(r"[A-F0-9]{64}", value):
        raise RuntimeError("hardware fingerprint is missing; first-boot provisioning is incomplete")
    return value


def device_token() -> str:
    return read_text(TOKEN_FILE)


def request(
    path: str,
    method: str = "GET",
    payload: dict[str, Any] | None = None,
    *,
    authenticated: bool = True,
) -> dict[str, Any]:
    body = None if payload is None else json.dumps(payload, separators=(",", ":")).encode()
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "User-Agent": f"juxin-orin-agent/{AGENT_VERSION}",
    }
    if authenticated:
        token = device_token()
        if not token:
            raise ApiError("device is not enrolled")
        headers["X-Orin-Device-Token"] = token

    req = urllib.request.Request(f"{API_BASE}{path}", data=body, method=method, headers=headers)
    raw = ""
    for attempt in range(REQUEST_RETRIES + 1):
        try:
            with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT) as response:
                raw = response.read().decode()
            break
        except urllib.error.HTTPError as error:
            detail = error.read().decode(errors="replace")[:500]
            retryable = error.code in {408, 425, 429} or error.code >= 500
            api_error = ApiError(
                f"HTTP {error.code}: {detail}",
                retryable=retryable,
                status=error.code,
            )
        except urllib.error.URLError as error:
            api_error = ApiError(f"network error: {error.reason}", retryable=True)
        except TimeoutError as error:
            api_error = ApiError(f"network timeout: {error}", retryable=True)

        if not api_error.retryable or attempt >= REQUEST_RETRIES:
            raise api_error
        time.sleep(RETRY_BASE_SECONDS * (2**attempt))

    try:
        result = json.loads(raw)
    except json.JSONDecodeError as error:
        raise ApiError("API returned invalid JSON") from error
    if result.get("code") != 200:
        raise ApiError(str(result.get("msg") or "API request failed"))
    return result


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
        first = [int(value) for value in Path("/proc/stat").read_text().splitlines()[0].split()[1:]]
        idle = first[3] + first[4]
        total = sum(first)
        time.sleep(0.15)
        second = [int(value) for value in Path("/proc/stat").read_text().splitlines()[0].split()[1:]]
        delta_total = sum(second) - total
        return round((1 - ((second[3] + second[4]) - idle) / delta_total) * 100, 1) if delta_total else 0.0
    except (OSError, IndexError, ValueError, ZeroDivisionError):
        return 0.0


def local_ip() -> str:
    connection = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        connection.connect(("1.1.1.1", 80))
        return connection.getsockname()[0]
    except OSError:
        try:
            return socket.gethostbyname(socket.gethostname())
        except OSError:
            return "127.0.0.1"
    finally:
        connection.close()


def cpu_model() -> str:
    model = platform.processor().strip()
    if model:
        return f"{platform.machine()} / {model}"
    for line in read_text("/proc/cpuinfo").splitlines():
        if line.lower().startswith(("model name", "hardware")) and ":" in line:
            return f"{platform.machine()} / {line.split(':', 1)[1].strip()}"
    return platform.machine()


def cuda_version() -> str:
    try:
        output = subprocess.check_output(["nvidia-smi"], text=True, stderr=subprocess.DEVNULL, timeout=5)
        match = re.search(r"CUDA Version:\s*([0-9.]+)", output)
        return match.group(1) if match else ""
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
        return ""


def tegra_metrics() -> dict[str, float]:
    try:
        output = subprocess.check_output(
            ["timeout", "3", "tegrastats", "--interval", "1000"],
            text=True,
            stderr=subprocess.DEVNULL,
        )
    except (OSError, subprocess.CalledProcessError):
        return {}
    temp = re.search(r"(?:tj|gpu)@([0-9.]+)C", output)
    power = re.search(r"VDD_IN\s+([0-9]+)mW", output)
    gpu = re.search(r"GR3D_FREQ\s+([0-9]+)%", output)
    result: dict[str, float] = {}
    if temp:
        result["gpu_temperature"] = float(temp.group(1))
    if power:
        result["power_watts"] = float(power.group(1)) / 1000
    if gpu:
        result["gpu_usage"] = float(gpu.group(1))
    return result


def report_payload() -> dict[str, Any]:
    memory, total_memory_mb = read_memory()
    l4t = read_text("/etc/nv_tegra_release").splitlines()
    payload: dict[str, Any] = {
        "sn": device_sn(),
        "cpu_load": f"{read_cpu():.1f}",
        "mem_load": f"{memory:.1f}",
        "cpu_model": cpu_model(),
        "agent_version": AGENT_VERSION,
        "image_version": IMAGE_VERSION,
        "hardware_fingerprint": hardware_fingerprint(),
        "ip": local_ip(),
        "device_model": read_text("/proc/device-tree/model"),
        "architecture": platform.machine(),
        "l4t_version": l4t[0] if l4t else "",
        "cuda_version": cuda_version(),
    }
    if total_memory_mb:
        payload["memory_total_mb"] = total_memory_mb
    payload.update(tegra_metrics())
    return payload


def ensure_enrolled(payload: dict[str, Any]) -> None:
    if device_token():
        return
    if not re.fullmatch(r"IMG-[0-9]{8}-[A-F0-9]{24}", IMAGE_LICENSE):
        raise ApiError("image license is missing or invalid")
    identity_fields = {"sn", "image_version", "hardware_fingerprint"}
    enrollment = {
        "sn": payload.get("sn") or device_sn(),
        "image_license": IMAGE_LICENSE,
        "image_version": payload.get("image_version") or IMAGE_VERSION,
        "hardware_fingerprint": payload.get("hardware_fingerprint") or hardware_fingerprint(),
        "telemetry": {key: value for key, value in payload.items() if key not in identity_fields},
    }
    response = request("/api/edge/enroll", "POST", enrollment, authenticated=False)
    data = response.get("data") or {}
    token = str(data.get("deviceToken") or "").strip()
    enrolled_sn = str(data.get("deviceSn") or "").strip()
    if enrolled_sn and enrolled_sn != device_sn():
        raise ApiError("enrollment returned a mismatched device identity")
    if len(token) < 32:
        raise ApiError("enrollment did not return a valid device token")
    atomic_write_secret(TOKEN_FILE, token)


def atomic_write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            json.dump(value, handle, separators=(",", ":"), ensure_ascii=True)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    finally:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass


def queue_outbox(kind: str, key: str, payload: dict[str, Any]) -> Path:
    if kind not in {"command", "task"}:
        raise ValueError(f"unsupported outbox kind: {kind}")
    digest = hashlib.sha256(f"{kind}:{key}".encode()).hexdigest()[:24]
    path = OUTBOX_DIR / f"{kind}-{digest}.json"
    atomic_write_json(path, {"kind": kind, "payload": payload})
    return path


def move_to_dead_letter(path: Path, reason: str) -> None:
    dead_letter_dir = OUTBOX_DIR / "dead-letter"
    dead_letter_dir.mkdir(parents=True, exist_ok=True)
    target = dead_letter_dir / path.name
    try:
        os.replace(path, target)
    except OSError as error:
        print(f"outbox dead-letter move failed for {path.name}: {error}", flush=True)
        return
    print(f"outbox item {path.name} needs operator review: {reason}", flush=True)


def flush_outbox(max_items: int = 20) -> int:
    if not OUTBOX_DIR.is_dir():
        return 0
    submitted = 0
    endpoints = {
        "command": "/api/edge/commands/submit",
        "task": "/api/edge/tasks/submit",
    }
    for path in sorted(OUTBOX_DIR.glob("*.json"))[:max_items]:
        try:
            record = json.loads(path.read_text(encoding="utf-8"))
            kind = record.get("kind")
            payload = record.get("payload")
            if kind not in endpoints or not isinstance(payload, dict):
                raise ValueError("invalid outbox record")
        except (OSError, ValueError, json.JSONDecodeError) as error:
            move_to_dead_letter(path, str(error))
            continue

        try:
            request(endpoints[kind], "POST", payload)
        except ApiError as error:
            if error.retryable:
                print(f"outbox delivery deferred: {error}", flush=True)
                break
            move_to_dead_letter(path, str(error))
            continue
        try:
            path.unlink()
        except OSError as error:
            print(f"outbox cleanup failed for {path.name}: {error}", flush=True)
            break
        submitted += 1
    return submitted


def submit_command(command: dict[str, Any]) -> None:
    command_text = str(command.get("command") or "")
    command_no = str(command.get("commandNo") or "").strip()
    if not command_no:
        print("ignored untracked legacy command without commandNo", flush=True)
        return
    result: dict[str, Any] = {
        "sn": device_sn(),
        "commandNo": command_no,
        "exitCode": 0,
        "resultText": "",
    }
    try:
        completed = subprocess.run(
            command_text,
            shell=True,
            capture_output=True,
            text=True,
            timeout=COMMAND_TIMEOUT,
        )
        result["exitCode"] = completed.returncode
        result["resultText"] = (completed.stdout + completed.stderr)[-12000:]
    except subprocess.TimeoutExpired as error:
        result["exitCode"] = 124
        result["resultText"] = f"command timeout: {error}"
    except OSError as error:
        result["exitCode"] = 127
        result["resultText"] = str(error)
    queue_outbox("command", command_no, result)
    flush_outbox()


def task_parameters(task: dict[str, Any]) -> dict[str, Any]:
    value = task.get("taskParams")
    if value is None or value == "":
        return {}
    if isinstance(value, dict):
        return value
    if not isinstance(value, str):
        raise ValueError("taskParams must be a JSON object")
    parsed = json.loads(value)
    if not isinstance(parsed, dict):
        raise ValueError("taskParams must be a JSON object")
    return parsed


def ollama_generate(task: dict[str, Any]) -> dict[str, Any]:
    model = str(task.get("modelName") or "").strip()
    prompt = str(task.get("prompt") or "").strip()
    if not model:
        raise ValueError("ollama task requires modelName")
    if not prompt:
        raise ValueError("ollama task requires prompt")

    params = task_parameters(task)
    payload: dict[str, Any] = {"model": model, "prompt": prompt, "stream": False}
    for key in ("system", "format", "options", "keep_alive"):
        if key in params:
            payload[key] = params[key]
    req = urllib.request.Request(
        f"{OLLAMA_API_BASE}/api/generate",
        data=json.dumps(payload, separators=(",", ":")).encode(),
        method="POST",
        headers={"Accept": "application/json", "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=TASK_TIMEOUT) as response:
            result = json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")[:500]
        raise RuntimeError(f"ollama HTTP {error.code}: {detail}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"ollama unavailable: {error.reason}") from error
    except json.JSONDecodeError as error:
        raise RuntimeError("ollama returned invalid JSON") from error
    if not isinstance(result, dict) or not isinstance(result.get("response"), str):
        raise RuntimeError("ollama response is missing generated text")
    return {
        "responseText": result["response"],
        "generateTokens": max(0, int(result.get("eval_count") or 0)),
    }


def configured_task_runner() -> Path | None:
    runtime_root = RUNTIME_DIR.resolve()
    runner = TASK_RUNNER.resolve()
    if not runner.is_relative_to(runtime_root):
        raise RuntimeError("configured task runner must be under the Orin runtime directory")
    if not runner.is_file() or not os.access(runner, os.X_OK):
        return None
    return runner


def run_external_task(task: dict[str, Any], runner: Path) -> dict[str, Any]:
    task_type = str(task.get("taskType") or "").strip().lower()
    completed = subprocess.run(
        [str(runner), task_type],
        input=json.dumps(task, separators=(",", ":")),
        capture_output=True,
        text=True,
        timeout=TASK_TIMEOUT,
        check=False,
    )
    if completed.returncode != 0:
        detail = (completed.stderr or completed.stdout or "runner failed")[-MAX_ERROR_TEXT:]
        raise RuntimeError(f"task runner exited {completed.returncode}: {detail}")
    try:
        result = json.loads(completed.stdout)
    except json.JSONDecodeError as error:
        raise RuntimeError("task runner returned invalid JSON") from error
    if not isinstance(result, dict):
        raise RuntimeError("task runner result must be a JSON object")
    return result


def execute_task(task: dict[str, Any]) -> dict[str, Any]:
    started = time.monotonic()
    task_id = task.get("id")
    result: dict[str, Any] = {
        "id": task_id,
        "deviceSn": device_sn(),
        "status": "failed",
        "responseText": "",
        "generateTokens": 0,
        "durationMs": 0,
        "errorMsg": "",
    }
    try:
        if task_id is None:
            raise ValueError("task is missing id")
        task_type = str(task.get("taskType") or "").strip().lower()
        if not task_type:
            raise ValueError("task is missing taskType")
        if task_type == "ollama":
            output = ollama_generate(task)
        else:
            runner = configured_task_runner()
            if runner is None:
                raise RuntimeError(f"unsupported task type: {task_type}")
            output = run_external_task(task, runner)

        response_text = output.get("responseText", output.get("response", ""))
        generate_tokens = output.get("generateTokens", output.get("generate_tokens", 0))
        result["status"] = "completed"
        result["responseText"] = str(response_text or "")[-MAX_RESULT_TEXT:]
        result["generateTokens"] = max(0, int(generate_tokens or 0))
    except (OSError, RuntimeError, ValueError, TypeError, json.JSONDecodeError, subprocess.TimeoutExpired) as error:
        result["errorMsg"] = str(error)[-MAX_ERROR_TEXT:]
    finally:
        result["durationMs"] = max(0, int((time.monotonic() - started) * 1000))
    return result


def fetch_task() -> dict[str, Any] | None:
    query = urllib.parse.urlencode({"sn": device_sn()})
    response = request(f"/api/edge/tasks/fetch?{query}")
    task = response.get("data")
    if task is None:
        return None
    if not isinstance(task, dict):
        raise ApiError("task API returned an invalid payload")
    return task


def poll_task_once() -> bool:
    task = fetch_task()
    if task is None:
        return False
    result = execute_task(task)
    queue_outbox("task", str(result.get("id")), result)
    flush_outbox()
    return True


def next_interval(response: dict[str, Any], current: int) -> int:
    value = (response.get("data") or {}).get("heartbeatInterval")
    try:
        return min(3600, max(10, int(value)))
    except (TypeError, ValueError):
        return current


def next_task_interval(response: dict[str, Any], current: int) -> int:
    value = (response.get("data") or {}).get("taskPollInterval")
    try:
        return min(3600, max(10, int(value)))
    except (TypeError, ValueError):
        return current


def loop() -> None:
    heartbeat_interval = DEFAULT_INTERVAL
    task_poll_interval = DEFAULT_TASK_POLL_INTERVAL
    next_heartbeat = 0.0
    next_task_poll = 0.0
    next_outbox_retry = 0.0
    while True:
        now = time.monotonic()
        if now >= next_outbox_retry:
            try:
                flush_outbox()
            except (OSError, ValueError) as error:
                print(f"outbox flush failed: {error}", flush=True)
            next_outbox_retry = time.monotonic() + OUTBOX_RETRY_INTERVAL

        now = time.monotonic()
        if now >= next_heartbeat:
            try:
                payload = report_payload()
                ensure_enrolled(payload)
                response = request("/api/edge/report", "POST", payload)
                heartbeat_interval = next_interval(response, heartbeat_interval)
                task_poll_interval = next_task_interval(response, task_poll_interval)
                data = response.get("data") or {}
                if data.get("action") == "execute_command":
                    submit_command(data)
            except (ApiError, OSError, RuntimeError, ValueError) as error:
                print(f"report failed: {error}", flush=True)
            next_heartbeat = time.monotonic() + heartbeat_interval

        now = time.monotonic()
        if now >= next_task_poll:
            try:
                poll_task_once()
            except (ApiError, OSError, RuntimeError, ValueError) as error:
                print(f"task poll failed: {error}", flush=True)
            next_task_poll = time.monotonic() + task_poll_interval

        next_event = min(next_heartbeat, next_outbox_retry, next_task_poll)
        time.sleep(max(0.2, min(1.0, next_event - time.monotonic())))


if __name__ == "__main__":
    loop()
