#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import json
import os
import stat
import tempfile
import unittest
from pathlib import Path
from unittest import mock


AGENT_PATH = Path(__file__).resolve().parents[1] / "agent" / "orin_agent.py"
VALID_LICENSE = "IMG-20260730-0123456789ABCDEF01234567"
VALID_SN = "ORIN-0123456789AB"
VALID_FINGERPRINT = "A" * 64


class FakeResponse:
    def __init__(self, payload: dict):
        self.payload = json.dumps(payload).encode()

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self):
        return self.payload


class OrinAgentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.state_dir = Path(self.temp.name)
        self.environment = mock.patch.dict(
            os.environ,
            {
                "ORIN_STATE_DIR": str(self.state_dir),
                "ORIN_DEVICE_SN": VALID_SN,
                "ORIN_HARDWARE_FINGERPRINT": VALID_FINGERPRINT,
                "ORIN_IMAGE_LICENSE": VALID_LICENSE,
                "ORIN_API_BASE_URL": "https://nvidia.juxinsuanli.cn",
                "ORIN_RUNTIME_DIR": str(self.state_dir / "runtime"),
                "ORIN_OUTBOX_DIR": str(self.state_dir / "outbox"),
                "ORIN_REQUEST_RETRIES": "2",
                "ORIN_RETRY_BASE_SECONDS": "0.001",
            },
            clear=False,
        )
        self.environment.start()
        spec = importlib.util.spec_from_file_location(f"orin_agent_test_{id(self)}", AGENT_PATH)
        self.agent = importlib.util.module_from_spec(spec)
        assert spec and spec.loader
        spec.loader.exec_module(self.agent)

    def tearDown(self):
        self.environment.stop()
        self.temp.cleanup()

    def test_enrollment_persists_device_token(self):
        token = "T" * 48
        self.agent.request = mock.Mock(
            return_value={"code": 200, "data": {"deviceSn": VALID_SN, "deviceToken": token}}
        )

        self.agent.ensure_enrolled(
            {
                "sn": VALID_SN,
                "image_version": "orin-l4t-36.4.7-test",
                "hardware_fingerprint": VALID_FINGERPRINT,
                "cpu_load": "12.5",
                "gpu_temperature": 42.0,
            }
        )

        self.assertEqual(token, self.agent.TOKEN_FILE.read_text().strip())
        mode = stat.S_IMODE(self.agent.TOKEN_FILE.stat().st_mode)
        self.assertEqual(0o600, mode)
        args, kwargs = self.agent.request.call_args
        self.assertEqual("/api/edge/enroll", args[0])
        self.assertEqual(VALID_LICENSE, args[2]["image_license"])
        self.assertEqual(VALID_SN, args[2]["sn"])
        self.assertEqual("orin-l4t-36.4.7-test", args[2]["image_version"])
        self.assertEqual(VALID_FINGERPRINT, args[2]["hardware_fingerprint"])
        self.assertEqual({"cpu_load": "12.5", "gpu_temperature": 42.0}, args[2]["telemetry"])
        self.assertFalse(kwargs["authenticated"])

    def test_authenticated_request_sends_device_token(self):
        self.agent.atomic_write_secret(self.agent.TOKEN_FILE, "secret-device-token")
        captured = {}

        def fake_urlopen(request, timeout):
            captured["request"] = request
            captured["timeout"] = timeout
            return FakeResponse({"code": 200, "data": {"action": "none"}})

        with mock.patch.object(self.agent.urllib.request, "urlopen", side_effect=fake_urlopen):
            result = self.agent.request("/api/edge/report", "POST", {"sn": VALID_SN})

        self.assertEqual(200, result["code"])
        self.assertEqual("secret-device-token", captured["request"].get_header("X-orin-device-token"))
        self.assertEqual(20, captured["timeout"])

    def test_business_error_is_not_treated_as_online(self):
        with mock.patch.object(
            self.agent.urllib.request,
            "urlopen",
            return_value=FakeResponse({"code": 500, "msg": "token rejected"}),
        ):
            with self.assertRaisesRegex(self.agent.ApiError, "token rejected"):
                self.agent.request("/api/edge/enroll", "POST", {}, authenticated=False)

    def test_server_heartbeat_interval_is_bounded(self):
        self.assertEqual(10, self.agent.next_interval({"data": {"heartbeatInterval": 1}}, 60))
        self.assertEqual(3600, self.agent.next_interval({"data": {"heartbeatInterval": 9999}}, 60))
        self.assertEqual(90, self.agent.next_interval({"data": {"heartbeatInterval": "90"}}, 60))

    def test_server_task_interval_is_independent_and_bounded(self):
        response = {"data": {"heartbeatInterval": 90, "taskPollInterval": 15}}

        self.assertEqual(90, self.agent.next_interval(response, 60))
        self.assertEqual(15, self.agent.next_task_interval(response, 60))
        self.assertEqual(3600, self.agent.next_task_interval({"data": {"taskPollInterval": 9999}}, 60))

    def test_retryable_network_error_uses_exponential_retry(self):
        self.agent.atomic_write_secret(self.agent.TOKEN_FILE, "secret-device-token")
        with mock.patch.object(
            self.agent.urllib.request,
            "urlopen",
            side_effect=[
                self.agent.urllib.error.URLError("offline"),
                FakeResponse({"code": 200, "data": None}),
            ],
        ) as urlopen, mock.patch.object(self.agent.time, "sleep") as sleep:
            response = self.agent.request("/api/edge/tasks/fetch?sn=ORIN-0123456789AB")

        self.assertEqual(200, response["code"])
        self.assertEqual(2, urlopen.call_count)
        sleep.assert_called_once_with(self.agent.RETRY_BASE_SECONDS)

    def test_fetch_task_uses_authenticated_device_identity(self):
        task = {"id": 8, "taskType": "ollama"}
        self.agent.request = mock.Mock(return_value={"code": 200, "data": task})

        self.assertEqual(task, self.agent.fetch_task())

        path = self.agent.request.call_args.args[0]
        self.assertEqual(f"/api/edge/tasks/fetch?sn={VALID_SN}", path)

    def test_ollama_task_builds_completed_result(self):
        task = {
            "id": 8,
            "taskType": "ollama",
            "modelName": "qwen2.5:3b",
            "prompt": "hello",
        }
        with mock.patch.object(
            self.agent,
            "ollama_generate",
            return_value={"responseText": "world", "generateTokens": 7},
        ):
            result = self.agent.execute_task(task)

        self.assertEqual("completed", result["status"])
        self.assertEqual("world", result["responseText"])
        self.assertEqual(7, result["generateTokens"])
        self.assertEqual(VALID_SN, result["deviceSn"])

    def test_unknown_task_type_is_failed_without_shell_execution(self):
        task = {"id": 9, "taskType": "arbitrary-shell", "taskParams": '{"command":"id"}'}
        with mock.patch.object(self.agent, "configured_task_runner", return_value=None), mock.patch.object(
            self.agent.subprocess, "run"
        ) as run:
            result = self.agent.execute_task(task)

        self.assertEqual("failed", result["status"])
        self.assertIn("unsupported task type", result["errorMsg"])
        run.assert_not_called()

    def test_poll_executes_and_submits_task_result(self):
        result = {
            "id": 10,
            "deviceSn": VALID_SN,
            "status": "completed",
            "responseText": "done",
            "generateTokens": 3,
            "durationMs": 20,
            "errorMsg": "",
        }
        self.agent.fetch_task = mock.Mock(return_value={"id": 10, "taskType": "ollama"})
        self.agent.execute_task = mock.Mock(return_value=result)
        self.agent.request = mock.Mock(return_value={"code": 200, "data": "ok"})

        self.assertTrue(self.agent.poll_task_once())

        self.agent.request.assert_called_once_with("/api/edge/tasks/submit", "POST", result)
        self.assertEqual([], list(self.agent.OUTBOX_DIR.glob("*.json")))

    def test_retryable_submission_remains_in_outbox(self):
        payload = {"id": 11, "deviceSn": VALID_SN, "status": "failed"}
        path = self.agent.queue_outbox("task", "11", payload)
        self.agent.request = mock.Mock(
            side_effect=self.agent.ApiError("backend unavailable", retryable=True)
        )

        self.assertEqual(0, self.agent.flush_outbox())

        self.assertTrue(path.is_file())
        self.assertEqual([], list((self.agent.OUTBOX_DIR / "dead-letter").glob("*.json")))

    def test_command_result_is_persisted_when_backend_is_unavailable(self):
        completed = self.agent.subprocess.CompletedProcess(
            args="echo ok", returncode=0, stdout="ok\n", stderr=""
        )
        self.agent.request = mock.Mock(
            side_effect=self.agent.ApiError("backend unavailable", retryable=True)
        )
        with mock.patch.object(self.agent.subprocess, "run", return_value=completed):
            self.agent.submit_command({"commandNo": "CMD-1", "command": "echo ok"})

        records = list(self.agent.OUTBOX_DIR.glob("command-*.json"))
        self.assertEqual(1, len(records))
        payload = json.loads(records[0].read_text())["payload"]
        self.assertEqual("CMD-1", payload["commandNo"])
        self.assertEqual("ok\n", payload["resultText"])


if __name__ == "__main__":
    unittest.main()
