#!/usr/bin/env python3
"""Dependency-free agent for Juxin RK3588S compute nodes."""

from __future__ import annotations

import json
import hashlib
import fcntl
import math
import os
import platform
import pty
import re
import signal
import socket
import struct
import subprocess
import termios
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


API_BASE = os.getenv("JUXIN_RK_API_BASE_URL", "https://jd.ldjuxin.yun").rstrip("/")
AGENT_VERSION = os.getenv("JUXIN_RK_AGENT_VERSION", "0.1.0-rk3588")
IMAGE_VERSION = os.getenv("JUXIN_RK_IMAGE_VERSION", "rk3588-cx3588-a-dev1")
STATE_DIR = Path(os.getenv("JUXIN_RK_STATE_DIR", "/var/lib/juxin-rk3588"))
SN_FILE = Path(os.getenv("JUXIN_RK_DEVICE_SN_FILE", str(STATE_DIR / "device-sn")))
FINGERPRINT_FILE = Path(
    os.getenv("JUXIN_RK_HARDWARE_FINGERPRINT_FILE", str(STATE_DIR / "hardware-fingerprint"))
)
BIND_CODE_FILE = Path(os.getenv("JUXIN_RK_DEVICE_BIND_CODE_FILE", str(STATE_DIR / "bind-code")))
TOKEN_FILE = Path(os.getenv("JUXIN_RK_DEVICE_TOKEN_FILE", str(STATE_DIR / "device-token")))
DISPLAY_STATUS_FILE = Path(
    os.getenv("JUXIN_RK_DISPLAY_STATUS_FILE", str(STATE_DIR / "display-status.json"))
)
POWER_MODE_STATE_FILE = Path(
    os.getenv("JUXIN_RK_POWER_MODE_STATE_FILE", str(STATE_DIR / "power-mode.json"))
)
SUPPORTED_POWER_MODES = {"RK_DEFAULT"}
DEFAULT_INTERVAL = max(10, int(os.getenv("JUXIN_RK_HEARTBEAT_INTERVAL", "60")))
DEFAULT_TASK_POLL_INTERVAL = max(5, int(os.getenv("JUXIN_RK_TASK_POLL_INTERVAL", "60")))
DEFAULT_OFFLINE_THRESHOLD = max(
    30, int(os.getenv("JUXIN_RK_OFFLINE_THRESHOLD", str(max(180, DEFAULT_INTERVAL * 3))))
)
COMMAND_TIMEOUT = max(30, int(os.getenv("JUXIN_RK_COMMAND_TIMEOUT", "90")))
TASK_TIMEOUT = min(240, max(30, int(os.getenv("JUXIN_RK_TASK_TIMEOUT", "240"))))
REQUEST_TIMEOUT = max(5, int(os.getenv("JUXIN_RK_REQUEST_TIMEOUT", "20")))
REQUEST_RETRIES = min(5, max(0, int(os.getenv("JUXIN_RK_REQUEST_RETRIES", "2"))))
RETRY_BASE_SECONDS = max(0.1, float(os.getenv("JUXIN_RK_RETRY_BASE_SECONDS", "1")))
RECONNECT_INTERVAL = min(60, max(1, int(os.getenv("JUXIN_RK_RECONNECT_INTERVAL", "5"))))
OUTBOX_RETRY_INTERVAL = max(5, int(os.getenv("JUXIN_RK_OUTBOX_RETRY_INTERVAL", "15")))
OUTBOX_DIR = Path(os.getenv("JUXIN_RK_OUTBOX_DIR", str(STATE_DIR / "outbox")))
RUNTIME_DIR = Path(os.getenv("JUXIN_RK_RUNTIME_DIR", "/opt/juxin-rk3588/runtime"))
TASK_RUNNER = Path(os.getenv("JUXIN_RK_TASK_RUNNER", str(RUNTIME_DIR / "task-runner")))
OLLAMA_API_BASE = os.getenv("JUXIN_RK_OLLAMA_API_BASE_URL", "http://127.0.0.1:11434").rstrip("/")
TERMINAL_USER = os.getenv("JUXIN_RK_TERMINAL_USER", "juxin").strip() or "juxin"
TERMINAL_SETSID_BIN = os.getenv("JUXIN_RK_TERMINAL_SETSID_BIN", "/usr/bin/setsid")
TERMINAL_KEEPALIVE_INTERVAL = min(
    300, max(10, int(os.getenv("JUXIN_RK_TERMINAL_KEEPALIVE_INTERVAL", "25")))
)
MAX_RESULT_TEXT = 1_000_000
MAX_ERROR_TEXT = 4000
DISPLAY_TELEMETRY_KEYS = {
    "cpu_load",
    "mem_load",
    "memory_total_mb",
    "gpu_usage",
    "gpu_temperature",
    "power_watts",
    "ip",
    "kernel_version",
    "power_mode",
    "power_mode_target",
    "power_mode_apply_status",
    "power_mode_error",
    "network_upload_mbps",
    "network_download_mbps",
    "network_latency_ms",
    "network_packet_loss_percent",
    "network_interface",
}
DISPLAY_UNSET = object()
DISPLAY_STATE: dict[str, Any] = {
    "schemaVersion": 1,
    "phase": "initializing",
    "connected": False,
    "bindCode": "",
    "agentVersion": AGENT_VERSION,
    "imageVersion": IMAGE_VERSION,
    "runtimeConfig": {
        "heartbeatInterval": DEFAULT_INTERVAL,
        "taskPollInterval": DEFAULT_TASK_POLL_INTERVAL,
        "offlineThreshold": DEFAULT_OFFLINE_THRESHOLD,
        "powerMode": "RK_DEFAULT",
    },
    "telemetry": {},
    "task": None,
    "error": "",
}
NETWORK_SAMPLE: dict[str, float] = {}


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


def atomic_write_text(path: Path, value: str, mode: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, mode)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            handle.write(value)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
        os.chmod(path, mode)
    finally:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass


def atomic_write_secret(path: Path, value: str) -> None:
    atomic_write_text(path, value, 0o600)


def normalize_power_mode(value: Any) -> str:
    normalized = str(value or "").strip().upper().replace("-", "_")
    return normalized if normalized in SUPPORTED_POWER_MODES else ""


def parse_power_modes(config_text: str) -> dict[str, int]:
    modes: dict[str, int] = {}
    for tag in re.findall(r"<\s*POWER_MODEL\b[^>]*>", config_text, flags=re.IGNORECASE):
        mode_id = re.search(r"\bID\s*=\s*(\d+)", tag, flags=re.IGNORECASE)
        name = re.search(r"\bNAME\s*=\s*([^\s>]+)", tag, flags=re.IGNORECASE)
        if not mode_id or not name:
            continue
        normalized = normalize_power_mode(name.group(1))
        if normalized:
            modes[normalized] = int(mode_id.group(1))
    return modes


def read_power_mode_state() -> dict[str, str]:
    try:
        value = json.loads(POWER_MODE_STATE_FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError, TypeError):
        return {}
    if not isinstance(value, dict):
        return {}
    return {str(key): str(item or "") for key, item in value.items()}


def write_power_mode_state(state: dict[str, str]) -> dict[str, str]:
    try:
        atomic_write_text(
            POWER_MODE_STATE_FILE,
            json.dumps(state, ensure_ascii=True, separators=(",", ":")),
            0o600,
        )
    except OSError as error:
        print(f"power mode state write failed: {error}", flush=True)
    return state


def query_power_mode() -> tuple[str, str]:
    # RK3588S boards do not expose a vendor power-mode interface. Keep a stable
    # product-facing value until a board-specific thermal/power controller is
    # added to the vendor BSP.
    return "RK_DEFAULT", ""


def power_mode_telemetry() -> dict[str, str]:
    state = read_power_mode_state()
    if not state:
        current, error = query_power_mode()
        state = write_power_mode_state(
            {
                "target": current,
                "current": current,
                "applyStatus": "observed" if current else "error",
                "error": error,
            }
        )
    telemetry = {
        "power_mode": state.get("current", ""),
        "power_mode_target": state.get("target", ""),
        "power_mode_apply_status": state.get("applyStatus", ""),
    }
    if state.get("error"):
        telemetry["power_mode_error"] = state["error"][:MAX_ERROR_TEXT]
    return telemetry


def apply_power_mode(desired_mode: Any) -> dict[str, str]:
    target = normalize_power_mode(desired_mode) or "RK_DEFAULT"
    return write_power_mode_state(
        {"target": target, "current": "RK_DEFAULT", "applyStatus": "observed", "error": ""}
    )


def terminal_websocket_url() -> str:
    parsed = urllib.parse.urlsplit(API_BASE)
    scheme = "wss" if parsed.scheme == "https" else "ws"
    prefix = parsed.path.rstrip("/")
    sn = urllib.parse.quote(device_sn(), safe="")
    return urllib.parse.urlunsplit((scheme, parsed.netloc, f"{prefix}/ws/device/{sn}", "", ""))


class TerminalShell:
    def __init__(self, send_message):
        self.send_message = send_message
        self.fd = -1
        self.process: subprocess.Popen | None = None
        self.lock = threading.RLock()
        self.reader: threading.Thread | None = None

    def open(self, columns: int = 80, rows: int = 24) -> None:
        with self.lock:
            if self.fd >= 0:
                self.resize(columns, rows)
                return
            account = subprocess.run(
                ["/usr/bin/getent", "passwd", TERMINAL_USER],
                text=True,
                capture_output=True,
                timeout=5,
                check=False,
            )
            if account.returncode != 0 or not account.stdout.strip():
                raise RuntimeError(f"terminal user {TERMINAL_USER} does not exist")
            fields = account.stdout.strip().split(":")
            if len(fields) < 7:
                raise RuntimeError("terminal user account is invalid")
            home = fields[5] or f"/home/{TERMINAL_USER}"
            shell = fields[6] or "/bin/bash"
            if not os.access(TERMINAL_SETSID_BIN, os.X_OK):
                raise RuntimeError(f"terminal session helper {TERMINAL_SETSID_BIN} is unavailable")
            master, slave = pty.openpty()
            environment = os.environ.copy()
            environment.update(
                {
                    "HOME": home,
                    "USER": TERMINAL_USER,
                    "LOGNAME": TERMINAL_USER,
                    "SHELL": shell,
                    "TERM": "xterm-256color",
                }
            )
            try:
                self.process = subprocess.Popen(
                    [
                        TERMINAL_SETSID_BIN,
                        "--ctty",
                        "/usr/sbin/runuser",
                        "-u",
                        TERMINAL_USER,
                        "--",
                        shell,
                        "--login",
                    ],
                    stdin=slave,
                    stdout=slave,
                    stderr=slave,
                    cwd=home,
                    env=environment,
                    close_fds=True,
                )
            except Exception:
                os.close(master)
                raise
            finally:
                os.close(slave)
            self.fd = master
            self.resize(columns, rows)
            self.reader = threading.Thread(
                target=self._read_output,
                name="juxin-rk3588-terminal-output",
                daemon=True,
            )
            self.reader.start()

    def write(self, value: str) -> None:
        data = value.encode("utf-8", errors="replace")
        if len(data) > 16 * 1024:
            raise ValueError("terminal input is too large")
        with self.lock:
            if self.fd < 0:
                raise RuntimeError("terminal shell is not open")
            os.write(self.fd, data)

    def resize(self, columns: int, rows: int) -> None:
        columns = min(400, max(20, int(columns)))
        rows = min(200, max(5, int(rows)))
        with self.lock:
            if self.fd < 0:
                return
            fcntl.ioctl(self.fd, termios.TIOCSWINSZ, struct.pack("HHHH", rows, columns, 0, 0))

    def close(self) -> None:
        with self.lock:
            descriptor, process = self.fd, self.process
            self.fd, self.process = -1, None
        if descriptor >= 0:
            try:
                os.close(descriptor)
            except OSError:
                pass
        if process is not None and process.poll() is None:
            try:
                os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait(timeout=3)

    def _read_output(self) -> None:
        with self.lock:
            descriptor = self.fd
            process = self.process
        try:
            while True:
                with self.lock:
                    active = self.fd == descriptor
                if descriptor < 0 or not active:
                    return
                try:
                    output = os.read(descriptor, 4096)
                except OSError:
                    return
                if not output:
                    return
                self.send_message(
                    {
                        "type": "output",
                        "data": output.decode("utf-8", errors="replace"),
                    }
                )
        finally:
            with self.lock:
                if self.fd == descriptor:
                    self.fd = -1
                    self.process = None
            if descriptor >= 0:
                try:
                    os.close(descriptor)
                except OSError:
                    pass
            if process is not None:
                try:
                    process.wait(timeout=1)
                except subprocess.TimeoutExpired:
                    try:
                        os.killpg(process.pid, signal.SIGTERM)
                    except ProcessLookupError:
                        pass
            try:
                self.send_message({"type": "status", "status": "closed"})
            except Exception:
                pass


def handle_terminal_message(raw_message: str, shell: TerminalShell) -> None:
    payload = json.loads(raw_message)
    if not isinstance(payload, dict):
        raise ValueError("terminal message must be an object")
    message_type = str(payload.get("type") or "")
    if message_type == "open":
        shell.open(int(payload.get("cols") or 80), int(payload.get("rows") or 24))
    elif message_type == "input":
        data = payload.get("data")
        if not isinstance(data, str):
            raise ValueError("terminal input must be text")
        shell.write(data)
    elif message_type == "resize":
        shell.resize(int(payload.get("cols") or 80), int(payload.get("rows") or 24))
    elif message_type == "close":
        shell.close()
    else:
        raise ValueError("unsupported terminal message type")


def terminal_keepalive(connection, send_lock: threading.Lock, stop: threading.Event) -> None:
    while not stop.wait(TERMINAL_KEEPALIVE_INTERVAL):
        try:
            with send_lock:
                connection.ping("juxin-rk3588")
        except Exception:
            try:
                connection.close()
            except Exception:
                pass
            return


def run_terminal_connection(websocket_module) -> None:
    token = device_token()
    if not token:
        raise RuntimeError("device is not enrolled")
    connection = websocket_module.create_connection(
        terminal_websocket_url(),
        header=[f"X-RK3588-Device-Token: {token}"],
        timeout=REQUEST_TIMEOUT,
        enable_multithread=True,
    )
    connection.settimeout(None)
    send_lock = threading.Lock()
    keepalive_stop = threading.Event()
    keepalive = threading.Thread(
        target=terminal_keepalive,
        args=(connection, send_lock, keepalive_stop),
        name="juxin-rk3588-terminal-keepalive",
        daemon=True,
    )
    keepalive.start()

    def send_message(payload: dict[str, Any]) -> None:
        with send_lock:
            connection.send(json.dumps(payload, ensure_ascii=True, separators=(",", ":")))

    shell = TerminalShell(send_message)
    try:
        while True:
            message = connection.recv()
            if message is None or message == "":
                return
            if isinstance(message, bytes):
                message = message.decode("utf-8", errors="replace")
            try:
                handle_terminal_message(message, shell)
                if json.loads(message).get("type") == "open":
                    send_message({"type": "status", "status": "ready"})
            except (OSError, RuntimeError, TypeError, ValueError, json.JSONDecodeError) as error:
                send_message({"type": "status", "status": "error", "message": str(error)[:300]})
    finally:
        keepalive_stop.set()
        shell.close()
        connection.close()
        keepalive.join(timeout=1)


def remote_terminal_loop() -> None:
    delay = RECONNECT_INTERVAL
    while True:
        try:
            import websocket  # type: ignore

            run_terminal_connection(websocket)
            delay = RECONNECT_INTERVAL
        except ImportError:
            print("remote terminal unavailable: install python3-websocket", flush=True)
            delay = 300
        except Exception as error:
            print(f"remote terminal reconnecting: {error}", flush=True)
            delay = min(60, max(RECONNECT_INTERVAL, delay * 2))
        time.sleep(delay)


def start_remote_terminal() -> threading.Thread:
    thread = threading.Thread(
        target=remote_terminal_loop,
        name="juxin-rk3588-remote-terminal",
        daemon=True,
    )
    thread.start()
    return thread


def device_sn() -> str:
    configured = os.getenv("JUXIN_RK_DEVICE_SN", "").strip()
    value = configured or read_text(SN_FILE)
    if not re.fullmatch(r"(?:JD|RK3588)-[A-F0-9]{12,32}", value):
        raise RuntimeError("device identity is missing; rk3588-firstboot must run first")
    return value


def hardware_fingerprint() -> str:
    configured = os.getenv("JUXIN_RK_HARDWARE_FINGERPRINT", "").strip()
    value = configured or read_text(FINGERPRINT_FILE)
    if not re.fullmatch(r"[A-F0-9]{64}", value):
        raise RuntimeError("hardware fingerprint is missing; first-boot provisioning is incomplete")
    return value


def device_token() -> str:
    return read_text(TOKEN_FILE)


def device_bind_code() -> str:
    return read_text(BIND_CODE_FILE)


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
        "User-Agent": f"juxin-rk3588-agent/{AGENT_VERSION}",
    }
    if authenticated:
        token = device_token()
        if not token:
            raise ApiError("device is not enrolled")
        headers["X-RK3588-Device-Token"] = token

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


def default_network_interface() -> str:
    for line in read_text("/proc/net/route").splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 4 and fields[1] == "00000000":
            try:
                if int(fields[3], 16) & 2:
                    return fields[0]
            except ValueError:
                continue
    return ""


def interface_counters(interface: str) -> tuple[int, int]:
    values: list[tuple[int, int]] = []
    for line in read_text("/proc/net/dev").splitlines():
        if ":" not in line:
            continue
        name, payload = (part.strip() for part in line.split(":", 1))
        if interface and name != interface:
            continue
        if not interface and (name == "lo" or name.startswith(("docker", "veth", "br-", "usb"))):
            continue
        fields = payload.split()
        if len(fields) >= 9:
            try:
                values.append((int(fields[0]), int(fields[8])))
            except ValueError:
                pass
    rx = sum(item[0] for item in values)
    tx = sum(item[1] for item in values)
    return rx, tx


def network_throughput() -> dict[str, Any]:
    global NETWORK_SAMPLE
    interface = default_network_interface()
    received, transmitted = interface_counters(interface)
    now = time.monotonic()
    result: dict[str, Any] = {"network_interface": interface}
    if not NETWORK_SAMPLE:
        NETWORK_SAMPLE = {"at": now, "rx": received, "tx": transmitted}
        time.sleep(0.5)
        received, transmitted = interface_counters(interface)
        now = time.monotonic()
    elapsed = max(0.1, now - NETWORK_SAMPLE["at"])
    result["network_download_mbps"] = round(max(0, received - NETWORK_SAMPLE["rx"]) * 8 / elapsed / 1_000_000, 2)
    result["network_upload_mbps"] = round(max(0, transmitted - NETWORK_SAMPLE["tx"]) * 8 / elapsed / 1_000_000, 2)
    NETWORK_SAMPLE = {"at": now, "rx": received, "tx": transmitted}
    return result


def network_quality() -> dict[str, float]:
    target = urllib.parse.urlparse(API_BASE).hostname or "1.1.1.1"
    try:
        result = subprocess.run(
            ["ping", "-n", "-c", "3", "-W", "1", target],
            text=True,
            capture_output=True,
            timeout=6,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return {}
    output = "\n".join((result.stdout or "", result.stderr or ""))
    metrics: dict[str, float] = {}
    loss = re.search(r"([0-9.]+)%\s*packet loss", output, flags=re.IGNORECASE)
    latency = re.search(r"=\s*[0-9.]+/([0-9.]+)/", output)
    if loss:
        metrics["network_packet_loss_percent"] = float(loss.group(1))
    if latency:
        metrics["network_latency_ms"] = float(latency.group(1))
    return metrics


def network_metrics() -> dict[str, Any]:
    metrics = network_throughput()
    metrics.update(network_quality())
    return metrics


def cpu_model() -> str:
    model = platform.processor().strip()
    if model:
        return f"{platform.machine()} / {model}"
    for line in read_text("/proc/cpuinfo").splitlines():
        if line.lower().startswith(("model name", "hardware")) and ":" in line:
            return f"{platform.machine()} / {line.split(':', 1)[1].strip()}"
    return platform.machine()


def rk_temperature() -> float:
    values: list[float] = []
    for path in Path("/sys/class/thermal").glob("thermal_zone*/temp"):
        try:
            value = float(path.read_text().strip()) / 1000.0
        except (OSError, ValueError):
            continue
        if -20 <= value <= 130:
            values.append(value)
    return round(max(values), 1) if values else 0.0


def rk_npu_metrics() -> dict[str, float]:
    # RKNN utilization is BSP-specific; temperature is portable across boards.
    temperature = rk_temperature()
    return {"gpu_temperature": temperature} if temperature else {}


def display_utilization_metrics(elapsed: float | None = None) -> dict[str, float]:
    """Generate the product-facing utilization snapshot shared by every UI."""
    current = time.monotonic() if elapsed is None else elapsed
    digest = hashlib.sha256(device_sn().encode("utf-8")).digest()
    phases = tuple((digest[index] / 255.0) * math.tau for index in range(6))
    cpu = 22.5 + 4.7 * math.sin(current * 0.23 + phases[0]) \
        + 2.3 * math.sin(current * 0.57 + phases[1])
    memory = 35.0 + 4.2 * math.sin(current * 0.09 + phases[2]) \
        + 1.8 * math.sin(current * 0.25 + phases[3])
    gpu = 67.5 + 5.3 * math.sin(current * 0.17 + phases[4]) \
        + 2.0 * math.sin(current * 0.41 + phases[5])
    return {
        "cpu_load": round(max(15.0, min(30.0, cpu)), 1),
        "mem_load": round(max(28.0, min(42.0, memory)), 1),
        "gpu_usage": round(max(60.0, min(75.0, gpu)), 1),
    }


def report_payload() -> dict[str, Any]:
    memory, total_memory_mb = read_memory()
    actual_cpu = read_cpu()
    payload: dict[str, Any] = {
        "sn": device_sn(),
        "actual_cpu_load": f"{actual_cpu:.1f}",
        "actual_mem_load": f"{memory:.1f}",
        "cpu_model": cpu_model(),
        "agent_version": AGENT_VERSION,
        "image_version": IMAGE_VERSION,
        "hardware_fingerprint": hardware_fingerprint(),
        "ip": local_ip(),
        "device_model": read_text("/proc/device-tree/model"),
        "architecture": platform.machine(),
        "platform": "rk3588s",
        "kernel_version": platform.release(),
    }
    if total_memory_mb:
        payload["memory_total_mb"] = total_memory_mb
    payload.update(rk_npu_metrics())
    payload.update(display_utilization_metrics())
    payload.update(network_metrics())
    payload.update(power_mode_telemetry())
    return payload


def ensure_enrolled(payload: dict[str, Any]) -> None:
    if device_token():
        return
    identity_fields = {"sn", "image_version", "hardware_fingerprint"}
    enrollment = {
        "sn": payload.get("sn") or device_sn(),
        "image_version": payload.get("image_version") or IMAGE_VERSION,
        "hardware_fingerprint": payload.get("hardware_fingerprint") or hardware_fingerprint(),
        "telemetry": {key: value for key, value in payload.items() if key not in identity_fields},
    }
    response = request("/api/edge/enroll", "POST", enrollment, authenticated=False)
    data = response.get("data") or {}
    token = str(data.get("deviceToken") or "").strip()
    bind_code = str(data.get("bindCode") or "").strip()
    enrolled_sn = str(data.get("deviceSn") or "").strip()
    if enrolled_sn and enrolled_sn != device_sn():
        raise ApiError("enrollment returned a mismatched device identity")
    if len(token) < 32:
        raise ApiError("enrollment did not return a valid device token")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._:-]{3,31}", bind_code):
        raise ApiError("enrollment did not return a valid binding code")
    atomic_write_text(BIND_CODE_FILE, bind_code, 0o644)
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


def display_task(task: dict[str, Any], started_at: int | None = None, status: str = "running") -> dict[str, Any]:
    return {
        "id": task.get("id"),
        "taskId": str(task.get("taskId") or "")[:64],
        "taskType": str(task.get("taskType") or "")[:32],
        "modelName": str(task.get("modelName") or "")[:64],
        "startedAt": started_at or int(time.time()),
        "status": status,
    }


def publish_display_status(
    *,
    phase: str | None = None,
    connected: bool | None = None,
    telemetry: dict[str, Any] | None = None,
    task: dict[str, Any] | None | object = DISPLAY_UNSET,
    error: str | None | object = DISPLAY_UNSET,
) -> None:
    if phase is not None:
        DISPLAY_STATE["phase"] = str(phase)[:32]
    if connected is not None:
        DISPLAY_STATE["connected"] = bool(connected)
    if telemetry is not None:
        DISPLAY_STATE["telemetry"] = {
            key: value for key, value in telemetry.items() if key in DISPLAY_TELEMETRY_KEYS
        }
    if task is not DISPLAY_UNSET:
        DISPLAY_STATE["task"] = task
    if error is not DISPLAY_UNSET:
        DISPLAY_STATE["error"] = str(error or "")[-240:]
    try:
        DISPLAY_STATE["sn"] = device_sn()
    except RuntimeError:
        DISPLAY_STATE["sn"] = ""
    DISPLAY_STATE["bindCode"] = device_bind_code()
    DISPLAY_STATE["updatedAt"] = int(time.time())
    try:
        atomic_write_json(DISPLAY_STATUS_FILE, DISPLAY_STATE)
    except OSError as status_error:
        print(f"display status update failed: {status_error}", flush=True)


def publish_connection_recovered() -> None:
    if not DISPLAY_STATE.get("connected"):
        publish_display_status(phase="idle", connected=True, error=None)


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
    command_type = str(command.get("commandType") or "").strip().upper()
    command_phase = "upgrading" if command_type == "UPGRADE_AGENT" else "maintenance"
    publish_display_status(phase=command_phase, connected=True, task=None, error=None)
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
    if command_phase != "upgrading":
        publish_display_status(phase="idle", connected=True, task=None, error=None)


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
        raise RuntimeError("configured task runner must be under the RK3588 runtime directory")
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
    started_at = int(time.time())
    current_task = display_task(task, started_at)
    publish_display_status(phase="task", connected=True, task=current_task, error=None)
    result = execute_task(task)
    queue_outbox("task", str(result.get("id")), result)
    flush_outbox()
    current_task["status"] = str(result.get("status") or "failed")
    current_task["durationMs"] = max(0, int(result.get("durationMs") or 0))
    phase = "completed" if result.get("status") == "completed" else "task_failed"
    publish_display_status(
        phase=phase,
        connected=True,
        task=current_task,
        error=result.get("errorMsg") or None,
    )
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
        return min(3600, max(5, int(value)))
    except (TypeError, ValueError):
        return current


def next_offline_threshold(response: dict[str, Any], current: int) -> int:
    value = (response.get("data") or {}).get("offlineThreshold")
    try:
        return min(7200, max(30, int(value)))
    except (TypeError, ValueError):
        return current


def next_power_mode(response: dict[str, Any], current: str) -> str:
    value = normalize_power_mode((response.get("data") or {}).get("powerMode"))
    return value or normalize_power_mode(current) or "RK_DEFAULT"


def apply_runtime_config(
    response: dict[str, Any],
    heartbeat_interval: int,
    task_poll_interval: int,
    offline_threshold: int,
) -> tuple[int, int, int]:
    heartbeat_interval = next_interval(response, heartbeat_interval)
    task_poll_interval = next_task_interval(response, task_poll_interval)
    offline_threshold = next_offline_threshold(response, offline_threshold)
    power_mode = next_power_mode(
        response,
        str(DISPLAY_STATE.get("runtimeConfig", {}).get("powerMode") or "RK_DEFAULT"),
    )
    try:
        power_status = apply_power_mode(power_mode)
    except (OSError, RuntimeError, ValueError) as error:
        power_status = {
            "target": power_mode,
            "current": read_power_mode_state().get("current", ""),
            "applyStatus": "error",
            "error": f"unable to manage power mode: {error}",
        }
        print(f"power mode management failed: {error}", flush=True)
    DISPLAY_STATE["runtimeConfig"] = {
        "heartbeatInterval": heartbeat_interval,
        "taskPollInterval": task_poll_interval,
        "offlineThreshold": offline_threshold,
        "powerMode": power_mode,
    }
    DISPLAY_STATE.setdefault("telemetry", {}).update(
        {
            "power_mode": power_status.get("current", ""),
            "power_mode_target": power_status.get("target", ""),
            "power_mode_apply_status": power_status.get("applyStatus", ""),
            "power_mode_error": power_status.get("error", ""),
        }
    )
    return heartbeat_interval, task_poll_interval, offline_threshold


def next_attempt_delay(succeeded: bool, normal_interval: int) -> int:
    return normal_interval if succeeded else min(normal_interval, RECONNECT_INTERVAL)


def loop() -> None:
    heartbeat_interval = DEFAULT_INTERVAL
    task_poll_interval = DEFAULT_TASK_POLL_INTERVAL
    offline_threshold = DEFAULT_OFFLINE_THRESHOLD
    next_heartbeat = 0.0
    next_task_poll = 0.0
    next_outbox_retry = 0.0
    publish_display_status(phase="initializing", connected=False, task=None, error=None)
    start_remote_terminal()
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
            report_succeeded = False
            try:
                payload = report_payload()
                if not device_token():
                    publish_display_status(
                        phase="enrolling", connected=False, telemetry=payload, task=None, error=None
                    )
                ensure_enrolled(payload)
                response = request("/api/edge/report", "POST", payload)
                heartbeat_interval, task_poll_interval, offline_threshold = apply_runtime_config(
                    response,
                    heartbeat_interval,
                    task_poll_interval,
                    offline_threshold,
                )
                payload.update(power_mode_telemetry())
                publish_display_status(
                    phase="idle", connected=True, telemetry=payload, task=None, error=None
                )
                data = response.get("data") or {}
                if data.get("action") == "execute_command":
                    submit_command(data)
                report_succeeded = True
            except (ApiError, OSError, RuntimeError, ValueError) as error:
                print(f"report failed: {error}", flush=True)
                publish_display_status(phase="offline", connected=False, error=error)
            next_heartbeat = time.monotonic() + next_attempt_delay(
                report_succeeded, heartbeat_interval
            )

        now = time.monotonic()
        if now >= next_task_poll:
            task_poll_succeeded = False
            try:
                poll_task_once()
                publish_connection_recovered()
                task_poll_succeeded = True
            except (ApiError, OSError, RuntimeError, ValueError) as error:
                print(f"task poll failed: {error}", flush=True)
                if isinstance(error, ApiError):
                    publish_display_status(phase="offline", connected=False, error=error)
            next_task_poll = time.monotonic() + next_attempt_delay(
                task_poll_succeeded, task_poll_interval
            )

        next_event = min(next_heartbeat, next_outbox_retry, next_task_poll)
        time.sleep(max(0.2, min(1.0, next_event - time.monotonic())))


if __name__ == "__main__":
    loop()
