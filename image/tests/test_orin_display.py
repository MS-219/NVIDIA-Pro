#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock


DISPLAY_PATH = Path(__file__).resolve().parents[1] / "agent" / "orin_display.py"
VALID_SN = "ORIN-A1B2C3D4E5F67890"


class OrinDisplayTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.state_dir = Path(self.temp.name)
        self.sn_file = self.state_dir / "device-sn"
        self.status_file = self.state_dir / "display-status.json"
        self.sn_file.write_text(VALID_SN + "\n", encoding="utf-8")
        self.environment = mock.patch.dict(
            os.environ,
            {
                "ORIN_DEVICE_SN_FILE": str(self.sn_file),
                "ORIN_DISPLAY_STATUS_FILE": str(self.status_file),
            },
            clear=False,
        )
        self.environment.start()
        spec = importlib.util.spec_from_file_location(f"orin_display_test_{id(self)}", DISPLAY_PATH)
        self.display = importlib.util.module_from_spec(spec)
        assert spec and spec.loader
        spec.loader.exec_module(self.display)

    def tearDown(self):
        self.environment.stop()
        self.temp.cleanup()

    def write_state(self, **values):
        state = {
            "schemaVersion": 1,
            "phase": "idle",
            "connected": True,
            "sn": VALID_SN,
            "bindCode": "",
            "agentVersion": "0.5.0-orin",
            "runtimeConfig": {},
            "updatedAt": 1000,
            "telemetry": {},
            "task": None,
            "error": "",
        }
        state.update(values)
        self.status_file.write_text(json.dumps(state), encoding="utf-8")

    def test_idle_copy_uses_confirmed_chinese_status(self):
        self.write_state()

        state = self.display.load_state(now=1001)
        copy = self.display.display_copy(state, now=1001)

        self.assertEqual("算力核心运行正常", copy["main"])
        self.assertEqual("计算引擎运行中", copy["engine"])
        self.assertEqual("安全链路已连接", copy["link"])
        self.assertEqual("在线", copy["badge"])

    def test_task_copy_shows_model_and_elapsed_time_without_prompt(self):
        self.write_state(
            phase="task",
            task={
                "id": 7,
                "taskType": "ollama",
                "modelName": "qwen2.5:3b",
                "startedAt": 970,
                "prompt": "must never be displayed",
            },
        )

        state = self.display.load_state(now=1001)
        copy = self.display.display_copy(state, now=1001)
        fallback = self.display.terminal_frame(state, 3)

        self.assertEqual("计算任务执行中", copy["main"])
        self.assertEqual("qwen2.5:3b  ·  已运行 00:31", copy["detail"])
        self.assertNotIn("must never be displayed", fallback)

    def test_stale_idle_state_is_rendered_as_reconnecting(self):
        self.write_state(updatedAt=100)

        state = self.display.load_state(now=1000)
        copy = self.display.display_copy(state, now=1000)

        self.assertEqual("offline", state["phase"])
        self.assertFalse(state["connected"])
        self.assertEqual("正在恢复网络连接", copy["main"])

    def test_stale_detection_uses_backend_offline_threshold(self):
        self.write_state(
            updatedAt=100,
            runtimeConfig={"heartbeatInterval": 90, "offlineThreshold": 300},
        )

        connected = self.display.load_state(now=399)
        stale = self.display.load_state(now=401)

        self.assertTrue(connected["connected"])
        self.assertEqual("idle", connected["phase"])
        self.assertFalse(stale["connected"])
        self.assertEqual("offline", stale["phase"])

    def test_completed_state_returns_to_idle_after_six_seconds(self):
        self.write_state(phase="completed", updatedAt=990, task={"id": 7})

        state = self.display.load_state(now=1000)

        self.assertEqual("idle", state["phase"])
        self.assertIsNone(state["task"])

    def test_display_does_not_advertise_maintenance_access(self):
        self.write_state()
        state = self.display.load_state(now=1001)

        fallback = self.display.terminal_frame(state, 3)
        source = DISPLAY_PATH.read_text(encoding="utf-8")

        self.assertNotIn("Maintenance: SSH", fallback)
        self.assertNotIn("远程维护：SSH", source)
        self.assertNotIn("本地终端：Ctrl+Alt+F2", source)

    def test_nvidia_brand_draws_eye_mark_before_wordmark(self):
        draw = mock.Mock()

        with mock.patch.object(self.display, "draw_pixel_word", return_value=999) as wordmark:
            end = self.display.draw_nvidia_brand(draw, 10, 20, 2, "#76B900")

        self.assertEqual(999, end)
        self.assertGreater(draw.rectangle.call_count, 0)
        wordmark.assert_called_once_with(draw, 54, 33, "NVIDIA", 2, "#76B900")

    def test_header_title_uses_green_pixel_text(self):
        draw = mock.Mock()
        selected_font = object()

        with mock.patch.object(self.display, "font", return_value=selected_font) as get_font, \
             mock.patch.object(self.display, "draw_pixel_text") as pixel_text:
            self.display.draw_header_title(draw, 640, 45, large=False, medium=False)

        get_font.assert_called_once_with(11, bold=True)
        pixel_text.assert_called_once_with(
            draw,
            640,
            45,
            "聚芯Orin边缘算力节点",
            selected_font,
            2,
            self.display.COLORS["green"],
        )

    def test_node_access_status_uses_product_language(self):
        self.assertEqual("节点已接入", self.display.node_access_status(True))
        self.assertEqual("节点接入中", self.display.node_access_status(False))

    def test_terminal_frame_uses_backend_managed_power_mode(self):
        state = self.display.default_state()
        state.update(
            {
                "connected": True,
                "phase": "idle",
                "runtimeConfig": {"powerMode": "25W"},
                "telemetry": {"power_mode": "25W"},
            }
        )

        frame = self.display.terminal_frame(state, 0)

        self.assertIn("GPU READY  |  25W  |  NODE ATTACHED", frame)
        self.assertNotIn("MAXN_SUPER", frame)

    def test_display_prefers_short_code_and_falls_back_to_machine_sn(self):
        self.write_state(bindCode="Orin-A1B2C3")
        state = self.display.load_state(now=1001)
        self.assertEqual("Orin-A1B2C3", self.display.display_identity(state))

        self.write_state(bindCode="")
        state = self.display.load_state(now=1001)
        self.assertEqual(VALID_SN, self.display.display_identity(state))

    @unittest.skipIf(os.environ.get("ORIN_TEST_PILLOW") != "1", "Pillow rendering is tested in image CI")
    def test_framebuffer_layout_renders_at_720p(self):
        self.write_state(
            telemetry={
                "gpu_usage": 37,
                "gpu_temperature": 51,
                "power_watts": 12.4,
                "mem_load": 28,
                "memory_total_mb": 8192,
                "ip": "192.0.2.10",
            }
        )
        state = self.display.load_state(now=1001)

        image = self.display.render_frame(state, 1280, 720, 5, [2, 8, 17, 37])

        self.assertEqual((1280, 720), image.size)
        self.assertGreater(len(image.getcolors(maxcolors=1_000_000) or []), 4)


if __name__ == "__main__":
    unittest.main()
