#!/usr/bin/env python3
"""Fullscreen framebuffer status display for Juxin Orin compute nodes."""

from __future__ import annotations

import ctypes
import fcntl
import json
import math
import mmap
import os
import signal
import struct
import sys
import time
from collections import deque
from functools import lru_cache
from pathlib import Path
from typing import Any

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:  # The image build installs python3-pil; keep a tty fallback.
    Image = None
    ImageDraw = None
    ImageFont = None


STATE_FILE = Path(os.getenv("ORIN_DISPLAY_STATUS_FILE", "/var/lib/juxin-orin/display-status.json"))
SN_FILE = Path(os.getenv("ORIN_DEVICE_SN_FILE", "/var/lib/juxin-orin/device-sn"))
TTY_PATH = Path(os.getenv("ORIN_DISPLAY_TTY", "/dev/tty1"))
FRAMEBUFFER_PATH = Path(os.getenv("ORIN_DISPLAY_FRAMEBUFFER", "/dev/fb0"))
FRAME_INTERVAL = 0.12
STALE_AFTER_SECONDS = 180
TASK_STALE_AFTER_SECONDS = 360

FBIOGET_VSCREENINFO = 0x4600
FBIOGET_FSCREENINFO = 0x4602
FBIO_WAITFORVSYNC = 0x4680
KDSETMODE = 0x4B3A
KD_TEXT = 0x00
KD_GRAPHICS = 0x01
VT_GETSTATE = 0x5603
VT_ACTIVATE = 0x5606
VT_WAITACTIVE = 0x5607

COLORS = {
    "background": "#050806",
    "panel": "#0A100C",
    "line": "#203126",
    "green": "#76B900",
    "green_dim": "#355A19",
    "white": "#F3F7F1",
    "muted": "#8FA095",
    "cyan": "#4FD1C5",
    "amber": "#E7B84B",
    "red": "#E45858",
}

PIXEL_GLYPHS = {
    "A": ("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    "D": ("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
    "I": ("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
    "N": ("10001", "11001", "11001", "10101", "10011", "10011", "10001"),
    "V": ("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
}


class FbBitField(ctypes.Structure):
    _fields_ = [
        ("offset", ctypes.c_uint32),
        ("length", ctypes.c_uint32),
        ("msb_right", ctypes.c_uint32),
    ]


class FbVarScreenInfo(ctypes.Structure):
    _fields_ = [
        ("xres", ctypes.c_uint32),
        ("yres", ctypes.c_uint32),
        ("xres_virtual", ctypes.c_uint32),
        ("yres_virtual", ctypes.c_uint32),
        ("xoffset", ctypes.c_uint32),
        ("yoffset", ctypes.c_uint32),
        ("bits_per_pixel", ctypes.c_uint32),
        ("grayscale", ctypes.c_uint32),
        ("red", FbBitField),
        ("green", FbBitField),
        ("blue", FbBitField),
        ("transp", FbBitField),
        ("nonstd", ctypes.c_uint32),
        ("activate", ctypes.c_uint32),
        ("height", ctypes.c_uint32),
        ("width", ctypes.c_uint32),
        ("accel_flags", ctypes.c_uint32),
        ("pixclock", ctypes.c_uint32),
        ("left_margin", ctypes.c_uint32),
        ("right_margin", ctypes.c_uint32),
        ("upper_margin", ctypes.c_uint32),
        ("lower_margin", ctypes.c_uint32),
        ("hsync_len", ctypes.c_uint32),
        ("vsync_len", ctypes.c_uint32),
        ("sync", ctypes.c_uint32),
        ("vmode", ctypes.c_uint32),
        ("rotate", ctypes.c_uint32),
        ("colorspace", ctypes.c_uint32),
        ("reserved", ctypes.c_uint32 * 4),
    ]


class FbFixScreenInfo(ctypes.Structure):
    _fields_ = [
        ("id", ctypes.c_char * 16),
        ("smem_start", ctypes.c_ulong),
        ("smem_len", ctypes.c_uint32),
        ("type", ctypes.c_uint32),
        ("type_aux", ctypes.c_uint32),
        ("visual", ctypes.c_uint32),
        ("xpanstep", ctypes.c_uint16),
        ("ypanstep", ctypes.c_uint16),
        ("ywrapstep", ctypes.c_uint16),
        ("line_length", ctypes.c_uint32),
        ("mmio_start", ctypes.c_ulong),
        ("mmio_len", ctypes.c_uint32),
        ("accel", ctypes.c_uint32),
        ("capabilities", ctypes.c_uint16),
        ("reserved", ctypes.c_uint16 * 2),
    ]


class VtState(ctypes.Structure):
    _fields_ = [
        ("active", ctypes.c_uint16),
        ("signal", ctypes.c_uint16),
        ("state", ctypes.c_uint16),
    ]


def clean_text(value: Any, limit: int = 80) -> str:
    text = str(value or "").replace("\x00", " ").replace("\n", " ").strip()
    return text[:limit]


def safe_number(value: Any, default: float = 0.0) -> float:
    try:
        number = float(value)
        return number if math.isfinite(number) else default
    except (TypeError, ValueError):
        return default


def read_sn() -> str:
    try:
        value = SN_FILE.read_text(encoding="utf-8", errors="ignore").strip()
    except OSError:
        return "ORIN-正在初始化"
    return clean_text(value, 40) or "ORIN-正在初始化"


def default_state() -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "phase": "initializing",
        "connected": False,
        "sn": read_sn(),
        "agentVersion": "",
        "imageVersion": "",
        "updatedAt": 0,
        "telemetry": {},
        "task": None,
        "error": "",
    }


def load_state(path: Path = STATE_FILE, now: float | None = None) -> dict[str, Any]:
    state = default_state()
    try:
        parsed = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(parsed, dict):
            state.update(parsed)
    except (OSError, ValueError, json.JSONDecodeError):
        return state

    state["sn"] = clean_text(state.get("sn"), 40) or read_sn()
    state["phase"] = clean_text(state.get("phase"), 32) or "initializing"
    state["agentVersion"] = clean_text(state.get("agentVersion"), 32)
    state["imageVersion"] = clean_text(state.get("imageVersion"), 48)
    state["error"] = clean_text(state.get("error"), 120)
    if not isinstance(state.get("telemetry"), dict):
        state["telemetry"] = {}
    if not isinstance(state.get("task"), dict):
        state["task"] = None

    current = time.time() if now is None else now
    updated_at = safe_number(state.get("updatedAt"))
    stale_limit = TASK_STALE_AFTER_SECONDS if state["phase"] == "task" else STALE_AFTER_SECONDS
    if updated_at and current - updated_at > stale_limit:
        state["connected"] = False
        if state["phase"] != "task":
            state["phase"] = "offline"
    if state["phase"] in {"completed", "task_failed"} and current - updated_at > 6:
        state["phase"] = "idle" if state.get("connected") else "offline"
        state["task"] = None
    return state


def display_copy(state: dict[str, Any], now: float | None = None) -> dict[str, str]:
    phase = state.get("phase")
    connected = bool(state.get("connected"))
    main = "算力核心运行正常"
    engine = "计算引擎运行中"
    link = "安全链路已连接"
    badge = "在线"
    color = COLORS["green"]

    if phase == "initializing":
        main, engine, link, badge = "系统正在初始化", "计算环境启动中", "正在建立安全链路", "启动中"
        color = COLORS["cyan"]
    elif phase == "enrolling":
        main, engine, link, badge = "节点身份正在接入", "计算环境启动中", "正在连接调度中心", "接入中"
        color = COLORS["cyan"]
    elif phase == "task":
        main, engine, link, badge = "计算任务执行中", "计算引擎高速运行", "安全链路已连接", "运算中"
        color = COLORS["green"]
    elif phase == "completed":
        main, engine = "本次计算已完成", "计算结果安全提交"
        color = COLORS["cyan"]
    elif phase == "task_failed":
        main, engine = "本次计算已结束", "调度中心正在处理状态"
        color = COLORS["amber"]
    elif phase == "upgrading":
        main, engine, link, badge = "系统正在安全升级", "请勿关闭设备电源", "安全链路已连接", "升级中"
        color = COLORS["amber"]
    elif phase == "maintenance":
        main, engine, badge = "远程运维进行中", "设备管理通道已启用", "维护中"
        color = COLORS["amber"]
    elif phase in {"offline", "error"} or not connected:
        main, engine, link, badge = "正在恢复网络连接", "计算核心保持运行", "调度链路重新连接中", "重连中"
        color = COLORS["amber"] if phase != "error" else COLORS["red"]

    task = state.get("task") or {}
    detail = ""
    if phase == "task":
        model = clean_text(task.get("modelName"), 32)
        task_type = clean_text(task.get("taskType"), 24)
        started_at = safe_number(task.get("startedAt"))
        elapsed = max(0, int((time.time() if now is None else now) - started_at)) if started_at else 0
        label = model or task_type or "计算任务"
        detail = f"{label}  ·  已运行 {elapsed // 60:02d}:{elapsed % 60:02d}"
    elif phase == "upgrading":
        detail = "系统组件正在更新"

    return {
        "main": main,
        "engine": engine,
        "link": link,
        "badge": badge,
        "color": color,
        "detail": detail,
    }


@lru_cache(maxsize=2)
def locate_font(bold: bool = False) -> str:
    names = (
        [
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        ]
        if bold
        else [
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
        ]
    )
    candidates = names + [
        "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
        "/System/Library/Fonts/PingFang.ttc",
        "/System/Library/Fonts/Hiragino Sans GB.ttc",
        "/System/Library/Fonts/STHeiti Medium.ttc",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for candidate in candidates:
        if Path(candidate).is_file():
            return candidate
    raise RuntimeError("no usable display font is installed")


@lru_cache(maxsize=16)
def font(size: int, bold: bool = False):
    if ImageFont is None:
        raise RuntimeError("Pillow is not installed")
    return ImageFont.truetype(locate_font(bold), size=size)


def text_width(draw, value: str, selected_font) -> int:
    box = draw.textbbox((0, 0), value, font=selected_font)
    return box[2] - box[0]


def centered_text(draw, canvas_width: int, y: int, value: str, selected_font, fill: str) -> None:
    draw.text(((canvas_width - text_width(draw, value, selected_font)) // 2, y), value, font=selected_font, fill=fill)


def draw_pixel_word(draw, x: int, y: int, word: str, cell: int, fill: str) -> int:
    cursor = x
    for letter in word:
        glyph = PIXEL_GLYPHS.get(letter)
        if glyph is None:
            cursor += cell * 3
            continue
        for row, bits in enumerate(glyph):
            for column, bit in enumerate(bits):
                if bit == "1":
                    left = cursor + column * cell
                    top = y + row * cell
                    draw.rectangle((left, top, left + cell - 1, top + cell - 1), fill=fill)
        cursor += cell * 6
    return cursor


def draw_compute_field(draw, width: int, y: int, frame: int, active: bool) -> None:
    field_width = min(820, width - 240)
    left = (width - field_width) // 2
    segment = max(8, field_width // 54)
    rows = 3
    speed = 2 if active else 1
    for row in range(rows):
        row_y = y + row * (segment + 8)
        direction = 1 if row % 2 == 0 else -1
        for index in range(field_width // segment):
            wave = (index * 3 + direction * frame * speed + row * 7) % 22
            if wave in {0, 1, 2, 3, 8, 9, 15}:
                color = COLORS["green"] if wave < 4 else COLORS["green_dim"]
                if active and wave in {0, 8, 15}:
                    color = COLORS["cyan"]
                x = left + index * segment
                draw.rectangle((x, row_y, x + segment - 3, row_y + segment - 3), fill=color)


def draw_sparkline(draw, values: list[float], left: int, top: int, width: int, height: int) -> None:
    if len(values) < 2:
        values = [0.0, 0.0]
    points = []
    for index, value in enumerate(values):
        x = left + int(index * width / max(1, len(values) - 1))
        y = top + height - int(min(100.0, max(0.0, value)) * height / 100)
        points.append((x, y))
    draw.line(points, fill=COLORS["cyan"], width=2)


def render_frame(
    state: dict[str, Any],
    width: int,
    height: int,
    frame: int,
    gpu_history: list[float] | None = None,
):
    if Image is None or ImageDraw is None:
        raise RuntimeError("Pillow is not installed")
    if width < 640 or height < 480:
        raise ValueError("display resolution must be at least 640x480")

    image = Image.new("RGB", (width, height), COLORS["background"])
    draw = ImageDraw.Draw(image)
    copy = display_copy(state)
    telemetry = state.get("telemetry") or {}
    large = height >= 900
    medium = height >= 650
    title_font = font(38 if large else 28 if medium else 22, bold=True)
    main_font = font(52 if large else 38 if medium else 30, bold=True)
    body_font = font(30 if large else 23 if medium else 18)
    small_font = font(22 if large else 17 if medium else 14)
    sn_font = font(42 if large else 32 if medium else 25, bold=True)

    margin = 34 if large else 22
    draw.rectangle((margin, margin, width - margin, height - margin), outline=COLORS["line"], width=2)
    scan_y = margin + ((frame * (4 if large else 3)) % max(1, height - margin * 2))
    draw.line((margin + 2, scan_y, width - margin - 2, scan_y), fill="#0D1711", width=1)

    cell = 5 if large else 4 if medium else 3
    draw_pixel_word(draw, margin + 36, margin + 28, "NVIDIA", cell, COLORS["green"])
    centered_text(draw, width, margin + 23, "聚芯边缘算力节点", title_font, COLORS["white"])

    pulse = 0.55 + 0.45 * ((math.sin(frame / 4) + 1) / 2)
    badge_color = copy["color"] if pulse > 0.72 else COLORS["green_dim"]
    badge = f"●  {copy['badge']}"
    badge_x = width - margin - 38 - text_width(draw, badge, body_font)
    draw.text((badge_x, margin + 28), badge, font=body_font, fill=badge_color)
    header_line_y = margin + (104 if large else 82)
    draw.line((margin + 32, header_line_y, width - margin - 32, header_line_y), fill=COLORS["line"], width=2)

    main_y = int(height * 0.19)
    centered_text(draw, width, main_y, copy["main"], main_font, COLORS["white"])
    field_y = int(height * 0.32)
    draw_compute_field(draw, width, field_y, frame, state.get("phase") == "task")
    engine_y = field_y + (94 if large else 72 if medium else 58)
    centered_text(draw, width, engine_y, copy["engine"], body_font, copy["color"])
    if copy["detail"]:
        centered_text(draw, width, engine_y + (48 if large else 35), copy["detail"], small_font, COLORS["muted"])

    link_y = int(height * 0.52)
    centered_text(draw, width, link_y, copy["link"], body_font, copy["color"])
    sn_y = int(height * 0.61)
    centered_text(draw, width, sn_y, clean_text(state.get("sn"), 40), sn_font, COLORS["white"])

    status_y = int(height * 0.72)
    scheduler = "调度中心已同步" if state.get("connected") else "调度中心连接中"
    status = f"GPU 已就绪    |    MAXN_SUPER    |    {scheduler}"
    centered_text(draw, width, status_y, status, body_font, COLORS["muted"])

    gpu = safe_number(telemetry.get("gpu_usage"))
    temperature = safe_number(telemetry.get("gpu_temperature"))
    power = safe_number(telemetry.get("power_watts"))
    memory_percent = safe_number(telemetry.get("mem_load"))
    total_mb = max(0, int(safe_number(telemetry.get("memory_total_mb"))))
    used_mb = int(total_mb * memory_percent / 100) if total_mb else 0
    memory_text = (
        f"{used_mb / 1024:.1f}/{total_mb / 1024:.1f}GB"
        if total_mb
        else f"{memory_percent:.0f}%" if "mem_load" in telemetry else "--"
    )
    gpu_text = f"{gpu:.0f}%" if "gpu_usage" in telemetry else "--"
    temperature_text = f"{temperature:.0f}°C" if "gpu_temperature" in telemetry else "--"
    power_text = f"{power:.1f}W" if "power_watts" in telemetry else "--"
    metrics_y = int(height * 0.84)
    metric_text = f"GPU {gpu_text}    温度 {temperature_text}    功耗 {power_text}    内存 {memory_text}"
    centered_text(draw, width, metrics_y, metric_text, body_font, COLORS["white"])

    history = gpu_history or [gpu]
    spark_width = min(360, width // 4)
    spark_left = margin + 45
    spark_top = metrics_y + (54 if large else 39)
    if spark_top + 30 < height - margin:
        draw_sparkline(draw, history, spark_left, spark_top, spark_width, 28 if large else 20)

    ip = clean_text(telemetry.get("ip"), 48) or "正在获取网络地址"
    version = clean_text(state.get("agentVersion"), 32)
    footer = f"{ip}    Agent {version}" if version else ip
    footer_y = height - margin - (38 if large else 28)
    draw.text((margin + 36, footer_y), footer, font=small_font, fill=COLORS["muted"])
    maintenance = "远程维护：SSH    本地终端：Ctrl+Alt+F2"
    maintenance_x = width - margin - 36 - text_width(draw, maintenance, small_font)
    draw.text((maintenance_x, footer_y), maintenance, font=small_font, fill=COLORS["muted"])
    return image


def terminal_frame(state: dict[str, Any], frame: int) -> str:
    telemetry = state.get("telemetry") or {}
    phase = state.get("phase")
    main = "COMPUTE CORE ONLINE"
    engine = "COMPUTE ENGINE ACTIVE"
    if phase in {"initializing", "enrolling"}:
        main, engine = "SYSTEM INITIALIZING", "SECURE UPLINK CONNECTING"
    elif phase == "task":
        main, engine = "COMPUTE SESSION ACTIVE", "COMPUTE ENGINE RUNNING"
    elif phase == "upgrading":
        main, engine = "SYSTEM UPDATE IN PROGRESS", "DO NOT POWER OFF"
    elif not state.get("connected"):
        main, engine = "UPLINK RECOVERING", "COMPUTE CORE REMAINS ACTIVE"
    offset = frame % 17
    moving = " " * offset + "###" + " " * (16 - offset)
    return "\n".join(
        [
            "NVIDIA                 JUXIN ORIN EDGE COMPUTE",
            "=" * 62,
            "",
            f"{main:^62}",
            f"{moving:^62}",
            f"{engine:^62}",
            "",
            f"{clean_text(state.get('sn'), 40):^62}",
            "",
            "GPU READY  |  MAXN_SUPER  |  ORCHESTRATOR SYNCED",
            f"GPU {safe_number(telemetry.get('gpu_usage')):3.0f}%   "
            f"TEMP {safe_number(telemetry.get('gpu_temperature')):3.0f}C   "
            f"POWER {safe_number(telemetry.get('power_watts')):4.1f}W",
            "",
            "Maintenance: SSH or Ctrl+Alt+F2",
        ]
    )


class VirtualTerminal:
    def __init__(self, path: Path):
        self.path = path
        self.number = int(path.name.replace("tty", ""))
        self.fd = os.open(path, os.O_RDWR | os.O_NOCTTY)
        fcntl.ioctl(self.fd, VT_ACTIVATE, self.number)
        fcntl.ioctl(self.fd, VT_WAITACTIVE, self.number)
        os.write(self.fd, b"\033[2J\033[H\033[?25l")
        self.graphics = False

    def set_graphics(self) -> None:
        fcntl.ioctl(self.fd, KDSETMODE, KD_GRAPHICS)
        self.graphics = True

    def is_active(self) -> bool:
        state = VtState()
        fcntl.ioctl(self.fd, VT_GETSTATE, state, True)
        return state.active == self.number

    def write_text(self, value: str) -> None:
        os.write(self.fd, ("\033[2J\033[H" + value).encode("utf-8", errors="replace"))

    def close(self) -> None:
        try:
            if self.graphics:
                fcntl.ioctl(self.fd, KDSETMODE, KD_TEXT)
            os.write(self.fd, b"\033[?25h\033[2J\033[H")
        except OSError:
            pass
        os.close(self.fd)


class Framebuffer:
    def __init__(self, path: Path):
        self.fd = os.open(path, os.O_RDWR)
        self.var = FbVarScreenInfo()
        self.fix = FbFixScreenInfo()
        fcntl.ioctl(self.fd, FBIOGET_VSCREENINFO, self.var, True)
        fcntl.ioctl(self.fd, FBIOGET_FSCREENINFO, self.fix, True)
        if self.var.bits_per_pixel != 32:
            os.close(self.fd)
            raise RuntimeError(f"unsupported framebuffer depth: {self.var.bits_per_pixel}")
        channel_offsets = (self.var.red.offset, self.var.green.offset, self.var.blue.offset)
        if channel_offsets == (16, 8, 0):
            self.raw_mode = "BGRX"
        elif channel_offsets == (0, 8, 16):
            self.raw_mode = "RGBX"
        else:
            os.close(self.fd)
            raise RuntimeError(f"unsupported framebuffer layout: {channel_offsets}")
        self.width = int(self.var.xres)
        self.height = int(self.var.yres)
        self.stride = int(self.fix.line_length)
        self.bytes_per_row = self.width * 4
        required = self.stride * int(self.var.yres_virtual)
        length = int(self.fix.smem_len) or required
        visible_end = (
            (int(self.var.yoffset) + self.height - 1) * self.stride
            + int(self.var.xoffset) * 4
            + self.bytes_per_row
        )
        if visible_end > length:
            os.close(self.fd)
            raise RuntimeError("framebuffer memory is smaller than the visible surface")
        self.memory = mmap.mmap(
            self.fd,
            length,
            flags=mmap.MAP_SHARED,
            prot=mmap.PROT_READ | mmap.PROT_WRITE,
        )

    def show(self, image) -> None:
        if image.size != (self.width, self.height):
            image = image.resize((self.width, self.height))
        raw = image.convert("RGB").tobytes("raw", self.raw_mode)
        x_offset = int(self.var.xoffset) * 4
        base = int(self.var.yoffset) * self.stride + x_offset
        if self.stride == self.bytes_per_row and x_offset == 0:
            self.memory[base : base + len(raw)] = raw
        else:
            for row in range(self.height):
                source = row * self.bytes_per_row
                target = base + row * self.stride
                self.memory[target : target + self.bytes_per_row] = raw[source : source + self.bytes_per_row]
        try:
            fcntl.ioctl(self.fd, FBIO_WAITFORVSYNC, struct.pack("I", 0))
        except OSError:
            pass

    def close(self) -> None:
        self.memory.close()
        os.close(self.fd)


def find_framebuffer() -> Path | None:
    if FRAMEBUFFER_PATH.exists():
        return FRAMEBUFFER_PATH
    candidates = sorted(Path("/dev").glob("fb*"))
    return candidates[0] if candidates else None


def main() -> int:
    running = True

    def stop(_signum, _frame):
        nonlocal running
        running = False

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)

    terminal = VirtualTerminal(TTY_PATH)
    framebuffer = None
    gpu_history: deque[float] = deque(maxlen=48)
    frame = 0
    try:
        framebuffer_path = find_framebuffer()
        if Image is not None and framebuffer_path is not None:
            try:
                locate_font()
                framebuffer = Framebuffer(framebuffer_path)
                terminal.set_graphics()
            except (OSError, RuntimeError) as error:
                if framebuffer is not None:
                    framebuffer.close()
                    framebuffer = None
                print(
                    f"juxin-orin-display: framebuffer initialization failed: {error}; using tty fallback",
                    file=sys.stderr,
                    flush=True,
                )
        else:
            reason = "Pillow unavailable" if Image is None else "framebuffer unavailable"
            print(f"juxin-orin-display: {reason}; using tty fallback", file=sys.stderr, flush=True)

        while running:
            state = load_state()
            gpu = safe_number((state.get("telemetry") or {}).get("gpu_usage"))
            if frame % 8 == 0 or not gpu_history:
                gpu_history.append(gpu)
            if terminal.is_active():
                if framebuffer is not None:
                    framebuffer.show(
                        render_frame(state, framebuffer.width, framebuffer.height, frame, list(gpu_history))
                    )
                else:
                    terminal.write_text(terminal_frame(state, frame))
            frame += 1
            time.sleep(FRAME_INTERVAL if state.get("phase") == "task" else 0.18)
    finally:
        if framebuffer is not None:
            framebuffer.close()
        terminal.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
