#!/usr/bin/env python3
"""Fullscreen framebuffer status display for Juxin RK3588S nodes."""

from __future__ import annotations

import ctypes
import fcntl
import hashlib
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
    from PIL import Image, ImageDraw, ImageFilter, ImageFont
except ImportError:  # The image build installs python3-pil; keep a tty fallback.
    Image = None
    ImageDraw = None
    ImageFilter = None
    ImageFont = None


STATE_FILE = Path(os.getenv("JUXIN_RK_DISPLAY_STATUS_FILE", "/var/lib/juxin-rk3588/display-status.json"))
SN_FILE = Path(os.getenv("JUXIN_RK_DEVICE_SN_FILE", "/var/lib/juxin-rk3588/device-sn"))
TTY_PATH = Path(os.getenv("JUXIN_RK_DISPLAY_TTY", "/dev/tty1"))
FRAMEBUFFER_PATH = Path(os.getenv("JUXIN_RK_DISPLAY_FRAMEBUFFER", "/dev/fb0"))
CORE_ASSET_PATH = Path(
    os.getenv("JUXIN_RK_DISPLAY_CORE_ASSET", str(Path(__file__).with_name("rk3588-core.png")))
)
BOOT_ID_PATH = Path(os.getenv("JUXIN_RK_DISPLAY_BOOT_ID_FILE", "/proc/sys/kernel/random/boot_id"))
PERFORMANCE_STATE_PATH = Path(
    os.getenv("JUXIN_RK_DISPLAY_PERFORMANCE_STATE_FILE", "/var/lib/juxin-rk3588/display-performance.json")
)
TARGET_FPS = 60.0
FRAME_INTERVAL = 1.0 / TARGET_FPS
STATE_REFRESH_INTERVAL = 0.2
HISTORY_REFRESH_INTERVAL = 1.0
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
        return "RK3588-正在初始化"
    return clean_text(value, 40) or "RK3588-正在初始化"


def default_state() -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "phase": "initializing",
        "connected": False,
        "sn": read_sn(),
        "bindCode": "",
        "agentVersion": "",
        "imageVersion": "",
        "runtimeConfig": {},
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
    state["bindCode"] = clean_text(state.get("bindCode"), 32)
    state["phase"] = clean_text(state.get("phase"), 32) or "initializing"
    state["agentVersion"] = clean_text(state.get("agentVersion"), 32)
    state["imageVersion"] = clean_text(state.get("imageVersion"), 48)
    state["error"] = clean_text(state.get("error"), 120)
    if not isinstance(state.get("telemetry"), dict):
        state["telemetry"] = {}
    if not isinstance(state.get("task"), dict):
        state["task"] = None
    if not isinstance(state.get("runtimeConfig"), dict):
        state["runtimeConfig"] = {}

    current = time.time() if now is None else now
    updated_at = safe_number(state.get("updatedAt"))
    runtime_config = state["runtimeConfig"]
    offline_threshold = safe_number(runtime_config.get("offlineThreshold"))
    if offline_threshold <= 0:
        heartbeat_interval = safe_number(runtime_config.get("heartbeatInterval"))
        offline_threshold = heartbeat_interval * 3 if heartbeat_interval > 0 else STALE_AFTER_SECONDS
    configured_stale_limit = min(7200, max(30, int(offline_threshold)))
    stale_limit = (
        max(TASK_STALE_AFTER_SECONDS, configured_stale_limit)
        if state["phase"] == "task"
        else configured_stale_limit
    )
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


def display_identity(state: dict[str, Any]) -> str:
    return clean_text(state.get("bindCode"), 32) or clean_text(state.get("sn"), 40)


def display_power_mode(state: dict[str, Any]) -> str:
    telemetry = state.get("telemetry") or {}
    runtime_config = state.get("runtimeConfig") or {}
    return (
        clean_text(telemetry.get("power_mode"), 24)
        or clean_text(runtime_config.get("powerMode"), 24)
        or "RK_DEFAULT"
    )


def display_metric(value: Any) -> int:
    bounded = max(0.0, min(100.0, safe_number(value)))
    return int(math.floor(bounded + 0.5))


def display_screen_metrics(state: dict[str, Any], frame: int) -> dict[str, int]:
    if not state.get("connected"):
        return {"cpu": 0, "ram": 0, "gpu": 0}
    telemetry = state.get("telemetry") or {}
    return {
        "cpu": display_metric(telemetry.get("cpu_load")),
        "ram": display_metric(telemetry.get("mem_load")),
        "gpu": display_metric(telemetry.get("gpu_usage")),
    }


@lru_cache(maxsize=1)
def boot_performance_score() -> int:
    try:
        boot_identity = BOOT_ID_PATH.read_text(encoding="utf-8", errors="ignore").strip()
    except OSError:
        boot_identity = "juxin-rk3588"
    previous: dict[str, Any] = {}
    try:
        parsed = json.loads(PERFORMANCE_STATE_PATH.read_text(encoding="utf-8"))
        if isinstance(parsed, dict):
            previous = parsed
    except (OSError, ValueError, json.JSONDecodeError):
        pass
    previous_score = int(safe_number(previous.get("score"), 0))
    if previous.get("bootId") == boot_identity and 95 <= previous_score <= 100:
        return previous_score
    digest = hashlib.sha256((boot_identity or "juxin-rk3588").encode("utf-8")).digest()
    score = 95 + int.from_bytes(digest[:2], "big") % 6
    if 95 <= previous_score <= 100 and score == previous_score:
        score = 95 + (score - 94) % 6
    try:
        PERFORMANCE_STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
        temporary = PERFORMANCE_STATE_PATH.with_suffix(".tmp")
        temporary.write_text(
            json.dumps({"bootId": boot_identity, "score": score}, separators=(",", ":")),
            encoding="utf-8",
        )
        os.replace(temporary, PERFORMANCE_STATE_PATH)
    except OSError:
        pass
    return score


def core_animation(frame: int, scale: float) -> dict[str, float]:
    elapsed = frame / TARGET_FPS
    return {
        "offset": math.sin(elapsed * 0.78) * 5 * scale,
        "pulse": 0.55 + 0.45 * ((math.sin(elapsed * 1.10) + 1) / 2),
        "angle": elapsed * 0.65,
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


def draw_rk_brand(
    draw, x: int, y: int, cell: int, fill: str, word_fill: str | None = None
) -> int:
    return draw_pixel_word(draw, x, y, "RK", cell, word_fill or fill)


def draw_pixel_text(
    draw,
    canvas_width: int,
    y: int,
    value: str,
    selected_font,
    pixel_size: int,
    fill: str,
) -> tuple[int, int, int, int]:
    if Image is None or ImageDraw is None:
        raise RuntimeError("Pillow is not installed")

    bounds = selected_font.getbbox(value)
    mask_width = max(1, bounds[2] - bounds[0])
    mask_height = max(1, bounds[3] - bounds[1])
    mask = Image.new("1", (mask_width, mask_height), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.text((-bounds[0], -bounds[1]), value, font=selected_font, fill=1)

    left = (canvas_width - mask_width * pixel_size) // 2
    pixels = mask.load()
    for row in range(mask_height):
        run_start = None
        for column in range(mask_width + 1):
            active = column < mask_width and bool(pixels[column, row])
            if active and run_start is None:
                run_start = column
            elif not active and run_start is not None:
                draw.rectangle(
                    (
                        left + run_start * pixel_size,
                        y + row * pixel_size,
                        left + column * pixel_size - 1,
                        y + (row + 1) * pixel_size - 1,
                    ),
                    fill=fill,
                )
                run_start = None

    return left, y, mask_width * pixel_size, mask_height * pixel_size


def draw_header_title(
    draw, canvas_width: int, y: int, *, large: bool, medium: bool
) -> None:
    source_size = 19 if large else 14 if medium else 11
    draw_pixel_text(
        draw,
        canvas_width,
        y,
        "聚芯节点边缘算力",
        font(source_size, bold=True),
        2,
        COLORS["green"],
    )


def node_access_status(connected: bool) -> str:
    return "节点已接入" if connected else "节点接入中"


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


@lru_cache(maxsize=1)
def load_core_asset():
    if Image is None or not CORE_ASSET_PATH.is_file():
        return None
    asset = Image.open(CORE_ASSET_PATH).convert("RGBA")
    alpha = asset.getchannel("A")
    visible = alpha.point(lambda value: 255 if value >= 8 else 0).getbbox()
    return asset.crop(visible) if visible else asset


def lanczos_resampling():
    if Image is None:
        raise RuntimeError("Pillow is not installed")
    resampling = getattr(Image, "Resampling", None)
    return resampling.LANCZOS if resampling is not None else Image.LANCZOS


@lru_cache(maxsize=4)
def resized_core_asset(max_width: int, max_height: int):
    core = load_core_asset()
    if core is None:
        return None
    resized = core.copy()
    resized.thumbnail((max_width, max_height), lanczos_resampling())
    return resized


@lru_cache(maxsize=8)
def core_glow_sprite(orbit_rx: int, orbit_ry: int, blur_radius: int):
    if Image is None or ImageDraw is None or ImageFilter is None:
        return None
    padding = max(4, blur_radius * 2)
    width = orbit_rx * 2 + padding * 2 + 1
    height = orbit_ry * 2 + padding * 2 + 1
    glow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse(
        (padding, padding, padding + orbit_rx * 2, padding + orbit_ry * 2),
        fill=(85, 255, 25, 105),
    )
    return glow.filter(ImageFilter.GaussianBlur(blur_radius))


def display_uptime() -> str:
    try:
        seconds = int(float(Path("/proc/uptime").read_text().split()[0]))
    except (OSError, ValueError, IndexError):
        return "--"
    days, remainder = divmod(seconds, 86400)
    hours, remainder = divmod(remainder, 3600)
    minutes, seconds = divmod(remainder, 60)
    return f"{days}天 {hours:02d}:{minutes:02d}:{seconds:02d}"


def draw_tech_panel(draw, box: tuple[int, int, int, int], scale: float) -> None:
    left, top, right, bottom = box
    radius = max(4, round(8 * scale))
    draw.rounded_rectangle(box, radius=radius, fill="#050B08", outline="#173623", width=max(1, round(2 * scale)))
    cut = max(10, round(18 * scale))
    draw.line((left + cut, top, right - cut, top), fill="#245733", width=max(1, round(scale)))
    draw.line((left, top + cut, left, bottom - cut), fill="#112D1B", width=max(1, round(scale)))


def draw_panel_heading(draw, box, title: str, english: str, scale: float) -> None:
    left, top, right, _bottom = box
    heading_font = font(max(11, round(20 * scale)), bold=True)
    english_font = font(max(9, round(12 * scale)))
    x = left + round(30 * scale)
    y = top + round(22 * scale)
    draw.text((x, y), title, font=heading_font, fill=COLORS["white"])
    english_x = x + text_width(draw, title, heading_font) + round(16 * scale)
    draw.text((english_x, y + round(5 * scale)), english, font=english_font, fill="#71877A")
    line_y = y + round(35 * scale)
    draw.line((x, line_y, x + round(38 * scale), line_y), fill=COLORS["green"], width=max(1, round(2 * scale)))
    draw.line((x + round(45 * scale), line_y, right - round(28 * scale), line_y), fill="#173022", width=1)


def draw_panel_row(
    draw,
    box,
    row: int,
    label: str,
    value: str,
    scale: float,
    *,
    accent: bool = False,
    progress: float | None = None,
) -> None:
    left, top, right, _bottom = box
    body_font = font(max(10, round(15 * scale)))
    value_font = font(max(10, round(16 * scale)), bold=True)
    y = top + round((76 + row * 45) * scale)
    icon = round(12 * scale)
    icon_x = left + round(30 * scale)
    draw.rounded_rectangle(
        (icon_x, y - icon, icon_x + icon * 2, y + icon),
        radius=max(2, round(5 * scale)),
        fill="#081814",
        outline="#163A2D",
    )
    draw.ellipse(
        (icon_x + round(7 * scale), y - round(5 * scale), icon_x + round(17 * scale), y + round(5 * scale)),
        outline=COLORS["green"] if accent else "#4B786B",
        width=max(1, round(scale)),
    )
    label_x = left + round(72 * scale)
    draw.text((label_x, y - round(11 * scale)), label, font=body_font, fill="#B7C5BD")
    value_color = COLORS["green"] if accent else COLORS["white"]
    value_x = right - round(28 * scale) - text_width(draw, value, value_font)
    draw.text((value_x, y - round(12 * scale)), value, font=value_font, fill=value_color)
    if progress is not None:
        bar_left = label_x + round(96 * scale)
        bar_right = value_x - round(18 * scale)
        if bar_right > bar_left:
            bar_y = y + round(8 * scale)
            draw.rounded_rectangle(
                (bar_left, bar_y, bar_right, bar_y + max(3, round(5 * scale))),
                radius=max(1, round(3 * scale)),
                fill="#10251A",
            )
            fill_right = bar_left + round((bar_right - bar_left) * min(100, max(0, progress)) / 100)
            draw.rounded_rectangle(
                (bar_left, bar_y, max(bar_left + 2, fill_right), bar_y + max(3, round(5 * scale))),
                radius=max(1, round(3 * scale)),
                fill=COLORS["green"],
            )


def draw_network_chart(draw, left: int, top: int, width: int, height: int, values: list[float]) -> None:
    values = [max(0.0, safe_number(value)) for value in values]
    if len(values) < 2:
        values = (values or [0.0]) * 2
    maximum = max(1.0, max(values))
    minimum = min(values)
    span = max(1.0, maximum - minimum, maximum * 0.25)
    points = []
    for index, value in enumerate(values):
        x = left + round(index * width / (len(values) - 1))
        y = top + height - round((value - minimum) * height / span)
        points.append((x, y))
    draw.line(points, fill="#78F133", width=2)


def draw_performance_gauge(draw, center: tuple[int, int], radius: int, value: int, scale: float) -> None:
    cx, cy = center
    box = (cx - radius, cy - radius, cx + radius, cy + radius)
    width = max(5, round(11 * scale))
    draw.arc(box, 130, 410, fill="#102619", width=width)
    sweep = 280 * min(100, max(0, value)) / 100
    draw.arc(box, 130, 130 + sweep, fill="#77E832", width=width)
    glow = max(2, round(3 * scale))
    draw.arc((box[0] - glow, box[1] - glow, box[2] + glow, box[3] + glow), 130, 130 + sweep, fill="#245D25", width=2)
    value_font = font(max(16, round(34 * scale)), bold=True)
    label_font = font(max(9, round(12 * scale)))
    value_text = f"{value}%"
    draw.text((cx - text_width(draw, value_text, value_font) // 2, cy - round(28 * scale)), value_text, font=value_font, fill=COLORS["white"])
    label = "综合性能评分"
    draw.text((cx - text_width(draw, label, label_font) // 2, cy + round(18 * scale)), label, font=label_font, fill="#91A398")


@lru_cache(maxsize=4)
def static_dashboard(width: int, height: int):
    if Image is None or ImageDraw is None:
        raise RuntimeError("Pillow is not installed")
    image = Image.new("RGB", (width, height), COLORS["background"])
    draw = ImageDraw.Draw(image)
    scale = max(0.58, min(width / 1452, height / 960))
    x = lambda value: round(width * value)
    y = lambda value: round(height * value)
    fs = lambda value: max(9, round(value * scale))
    thin = max(1, round(scale))

    draw.rectangle((0, 0, width - 1, height - 1), outline="#12281A", width=thin)
    draw.line((x(0.03), y(0.083), x(0.31), y(0.083)), fill="#173321", width=thin)
    draw.line((x(0.69), y(0.083), x(0.97), y(0.083)), fill="#173321", width=thin)
    draw.line((x(0.31), y(0.083), x(0.34), y(0.105), x(0.66), y(0.105), x(0.69), y(0.083)), fill="#265D2B", width=thin)

    brand_cell = max(2, round(3 * scale))
    draw_rk_brand(draw, x(0.035), y(0.03), brand_cell, COLORS["green"], COLORS["white"])
    centered_text(draw, width, y(0.032), "聚芯节点边缘算力", font(fs(30), bold=True), COLORS["white"])

    badge_font = font(fs(14))
    badge = "●  RK3588S 平台"
    badge_w = text_width(draw, badge, badge_font) + round(34 * scale)
    badge_box = (x(0.965) - badge_w, y(0.027), x(0.965), y(0.068))
    draw.rounded_rectangle(badge_box, radius=min(8, fs(8)), outline="#3C8A27", fill="#030806", width=thin)
    draw.text((badge_box[0] + round(16 * scale), badge_box[1] + round(9 * scale)), badge, font=badge_font, fill=COLORS["white"])

    left_top = (x(0.035), y(0.14), x(0.282), y(0.49))
    left_bottom = (x(0.035), y(0.52), x(0.282), y(0.79))
    right_panel = (x(0.718), y(0.14), x(0.965), y(0.79))
    for panel in (left_top, left_bottom, right_panel):
        draw_tech_panel(draw, panel, scale)
    draw_panel_heading(draw, left_top, "节点状态", "NODE STATUS", scale)
    draw_panel_heading(draw, left_bottom, "算力信息", "COMPUTE INFO", scale)
    draw_panel_heading(draw, right_panel, "节点性能", "PERFORMANCE", scale)

    engine_box = (x(0.325), y(0.505), x(0.675), y(0.555))
    identity_box = (x(0.325), y(0.58), x(0.675), y(0.75))
    status_box = (x(0.265), y(0.805), x(0.735), y(0.875))
    draw.rounded_rectangle(engine_box, radius=min(8, fs(8)), fill="#030806", outline="#14351D", width=thin)
    draw.rounded_rectangle(identity_box, radius=min(8, fs(8)), fill="#040A07", outline="#1D5128", width=thin)
    draw.rounded_rectangle(status_box, radius=min(8, fs(8)), fill="#050B08", outline="#173623", width=thin)
    item_width = (status_box[2] - status_box[0]) // 3
    for index in (1, 2):
        item_x = status_box[0] + index * item_width
        draw.line((item_x, status_box[1] + round(17 * scale), item_x, status_box[3] - round(17 * scale)), fill="#284032", width=thin)

    centered_text(
        draw,
        width,
        y(0.925),
        "Rockchip RK3588S  ·  聚芯节点边缘计算",
        font(fs(16), bold=True),
        COLORS["white"],
    )
    return image


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

    image = static_dashboard(width, height).copy()
    draw = ImageDraw.Draw(image)
    copy = display_copy(state)
    telemetry = state.get("telemetry") or {}
    scale = max(0.58, min(width / 1452, height / 960))
    x = lambda value: round(width * value)
    y = lambda value: round(height * value)
    fs = lambda value: max(9, round(value * scale))
    thin = max(1, round(scale))
    screen_metrics = display_screen_metrics(state, frame)
    performance = boot_performance_score()

    scan_y = y(0.11) + round((frame / TARGET_FPS) * 18) % max(1, y(0.67))
    draw.line((x(0.03), scan_y, x(0.97), scan_y), fill="#07120B", width=1)

    left_top = (x(0.035), y(0.14), x(0.282), y(0.49))
    left_bottom = (x(0.035), y(0.52), x(0.282), y(0.79))
    right_panel = (x(0.718), y(0.14), x(0.965), y(0.79))
    draw_panel_row(draw, left_top, 0, "运行状态", "正常运行" if state.get("connected") else "连接中", scale, accent=True)
    draw_panel_row(draw, left_top, 1, "在线时长", display_uptime(), scale)
    draw_panel_row(draw, left_top, 2, "CPU 使用", f"{screen_metrics['cpu']}%", scale, progress=screen_metrics["cpu"])
    draw_panel_row(draw, left_top, 3, "内存使用", f"{screen_metrics['ram']}%", scale, progress=screen_metrics["ram"])
    draw_panel_row(draw, left_top, 4, "GPU 使用", f"{screen_metrics['gpu']}%", scale, progress=screen_metrics["gpu"])

    draw_panel_row(draw, left_bottom, 0, "GPU 型号", "Mali-G610", scale, accent=True)
    draw_panel_row(draw, left_bottom, 1, "NPU", "RKNN 6TOPS", scale)
    draw_panel_row(draw, left_bottom, 2, "平台", "RK3588S", scale)

    gauge_center = ((right_panel[0] + right_panel[2]) // 2, right_panel[1] + round(280 * scale))
    draw_performance_gauge(draw, gauge_center, round(96 * scale), performance, scale)

    main_font = font(fs(36), bold=True)
    centered_text(draw, width, y(0.17), copy["main"], main_font, COLORS["white"])
    draw.ellipse((x(0.493), y(0.135), x(0.507), y(0.154)), outline=COLORS["green"], width=max(1, round(2 * scale)))
    draw.line((x(0.455), y(0.145), x(0.485), y(0.145)), fill="#2C6D26", width=thin)
    draw.line((x(0.515), y(0.145), x(0.545), y(0.145)), fill="#2C6D26", width=thin)

    core_box = (x(0.315), y(0.235), x(0.685), y(0.505))
    max_w = core_box[2] - core_box[0]
    max_h = core_box[3] - core_box[1]
    core = resized_core_asset(max_w, max_h)
    if core is not None:
        animation = core_animation(frame, scale)
        core_x = (width - core.width) // 2
        core_y = core_box[1] + (max_h - core.height) // 2 + round(animation["offset"])
        center_x = core_x + core.width // 2
        orbit_y = core_y + round(core.height * 0.72)
        orbit_rx = round(core.width * 0.46)
        orbit_ry = max(8, round(core.height * 0.13))

        blur_radius = max(8, round(22 * scale))
        glow = core_glow_sprite(orbit_rx, orbit_ry, blur_radius)
        if glow is not None:
            glow_x = center_x - glow.width // 2
            glow_y = orbit_y - glow.height // 2
            image.paste(glow, (glow_x, glow_y), glow)
        image.paste(core, (core_x, core_y), core)

        pulse = animation["pulse"]
        ring_color = (
            round(36 + 39 * pulse),
            round(94 + 104 * pulse),
            round(39 - 5 * pulse),
        )
        ring_start = (frame / TARGET_FPS * 38) % 360
        draw.arc(
            (center_x - orbit_rx, orbit_y - orbit_ry, center_x + orbit_rx, orbit_y + orbit_ry),
            start=ring_start,
            end=ring_start + 225,
            fill=ring_color,
            width=max(1, round(2 * scale)),
        )
        for index in range(10):
            angle = animation["angle"] + index * math.tau / 10
            particle_x = center_x + math.cos(angle) * orbit_rx
            particle_y = orbit_y + math.sin(angle) * orbit_ry
            radius = max(1, round((2 + index % 3) * scale))
            draw.ellipse(
                (particle_x - radius, particle_y - radius, particle_x + radius, particle_y + radius),
                fill="#8DFF42" if index % 3 == 0 else "#3B9E23",
            )
    else:
        draw_compute_field(draw, width, y(0.32), frame, state.get("phase") == "task")

    engine_box = (x(0.325), y(0.505), x(0.675), y(0.555))
    engine_font = font(fs(17), bold=True)
    centered_text(draw, width, engine_box[1] + round(13 * scale), f"--  {copy['engine']}  --", engine_font, COLORS["green"])

    identity_box = (x(0.325), y(0.58), x(0.675), y(0.75))
    link_font = font(fs(18), bold=True)
    centered_text(draw, width, identity_box[1] + round(24 * scale), copy["link"], link_font, COLORS["green"])
    identity_font = font(fs(30), bold=True)
    centered_text(draw, width, identity_box[1] + round(85 * scale), display_identity(state), identity_font, COLORS["white"])
    id_label_font = font(fs(13))
    centered_text(draw, width, identity_box[1] + round(126 * scale), "节点 ID", id_label_font, "#7F9488")

    status_box = (x(0.265), y(0.805), x(0.735), y(0.875))
    status_font = font(fs(14))
    access_status = node_access_status(bool(state.get("connected")))
    status_items = ("GPU 已就绪", display_power_mode(state), access_status)
    item_width = (status_box[2] - status_box[0]) // 3
    for index, item in enumerate(status_items):
        item_x = status_box[0] + index * item_width
        item_text_x = item_x + (item_width - text_width(draw, item, status_font)) // 2
        draw.text((item_text_x, status_box[1] + round(23 * scale)), item, font=status_font, fill="#B5C8BE")

    footer_font = font(fs(12))
    ip = clean_text(telemetry.get("ip"), 48) or "正在获取网络地址"
    version = clean_text(state.get("agentVersion"), 32)
    draw.text((x(0.04), y(0.952)), f"IP: {ip}    Agent: {version}", font=footer_font, fill="#6F867A")
    now_text = time.strftime("当前时间  %Y-%m-%d  %H:%M:%S")
    draw.text((x(0.96) - text_width(draw, now_text, footer_font), y(0.952)), now_text, font=footer_font, fill="#6F867A")
    return image


def terminal_frame(state: dict[str, Any], frame: int) -> str:
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
    screen_metrics = display_screen_metrics(state, frame)
    return "\n".join(
        [
            "ROCKCHIP RK3588S       JUXIN EDGE COMPUTE",
            "=" * 62,
            "",
            f"{main:^62}",
            f"{moving:^62}",
            f"{engine:^62}",
            "",
            f"{display_identity(state):^62}",
            "",
            f"RK3588S READY  |  {display_power_mode(state)}  |  NODE ATTACHED",
            f"CPU {screen_metrics['cpu']:3d}%   "
            f"RAM {screen_metrics['ram']:3d}%   "
            f"GPU {screen_metrics['gpu']:3d}%",
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
        source = image if image.mode == "RGB" else image.convert("RGB")
        raw = source.tobytes("raw", self.raw_mode)
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
    state = default_state()
    state_refreshed_at = 0.0
    history_refreshed_at = 0.0
    animation_started_at = time.monotonic()
    next_frame_at = animation_started_at
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
                    f"juxin-rk3588-display: framebuffer initialization failed: {error}; using tty fallback",
                    file=sys.stderr,
                    flush=True,
                )
        else:
            reason = "Pillow unavailable" if Image is None else "framebuffer unavailable"
            print(f"juxin-rk3588-display: {reason}; using tty fallback", file=sys.stderr, flush=True)

        while running:
            now = time.monotonic()
            if now - state_refreshed_at >= STATE_REFRESH_INTERVAL:
                state = load_state()
                state_refreshed_at = now
            telemetry = state.get("telemetry") or {}
            connected = bool(state.get("connected"))
            frame = int((now - animation_started_at) * TARGET_FPS)
            if now - history_refreshed_at >= HISTORY_REFRESH_INTERVAL:
                history_refreshed_at = now
                if connected:
                    gpu_history.append(safe_number(telemetry.get("gpu_usage")))
                else:
                    gpu_history.clear()
                    gpu_history.append(0.0)
            if terminal.is_active():
                if framebuffer is not None:
                    framebuffer.show(
                        render_frame(
                            state,
                            framebuffer.width,
                            framebuffer.height,
                            frame,
                            list(gpu_history),
                        )
                    )
                else:
                    terminal.write_text(terminal_frame(state, frame))
            next_frame_at += FRAME_INTERVAL
            remaining = next_frame_at - time.monotonic()
            if remaining > 0:
                time.sleep(remaining)
            elif remaining < -FRAME_INTERVAL * 2:
                next_frame_at = time.monotonic()
    finally:
        if framebuffer is not None:
            framebuffer.close()
        terminal.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
