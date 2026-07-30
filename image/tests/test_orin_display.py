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
VALID_SN = "ORIN-A1B2C3D4E5F6"


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
            "agentVersion": "0.5.0-orin",
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

    def test_completed_state_returns_to_idle_after_six_seconds(self):
        self.write_state(phase="completed", updatedAt=990, task={"id": 7})

        state = self.display.load_state(now=1000)

        self.assertEqual("idle", state["phase"])
        self.assertIsNone(state["task"])

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
