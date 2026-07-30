#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import json
import os
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest import mock
from urllib.parse import parse_qs, urlparse


AGENT_PATH = Path(__file__).resolve().parents[1] / "agent" / "orin_agent.py"
VALID_LICENSE = "IMG-20260730-0123456789ABCDEF01234567"
VALID_SN = "ORIN-0123456789AB"
VALID_FINGERPRINT = "A" * 64
DEVICE_TOKEN = "T" * 43


class ContractHandler(BaseHTTPRequestHandler):
    submissions: list[dict] = []
    reports: list[dict] = []
    enrollments: list[dict] = []

    def log_message(self, *_args):
        pass

    def read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", "0"))
        return json.loads(self.rfile.read(length) or b"{}")

    def write_result(self, data) -> None:
        body = json.dumps({"code": 200, "msg": "success", "data": data}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def require_token(self) -> None:
        if self.headers.get("X-Orin-Device-Token") != DEVICE_TOKEN:
            raise AssertionError("missing device token")

    def do_POST(self):
        if self.path == "/api/edge/enroll":
            payload = self.read_json()
            self.__class__.enrollments.append(payload)
            self.write_result({"deviceSn": VALID_SN, "deviceId": 1, "bindCode": "123456", "deviceToken": DEVICE_TOKEN})
            return
        self.require_token()
        if self.path == "/api/edge/report":
            payload = self.read_json()
            self.__class__.reports.append(payload)
            self.write_result({"action": "none", "heartbeatInterval": 30, "taskPollInterval": 17})
            return
        if self.path == "/api/edge/tasks/submit":
            self.__class__.submissions.append(self.read_json())
            self.write_result("Contribution recorded")
            return
        self.send_error(404)

    def do_GET(self):
        self.require_token()
        parsed = urlparse(self.path)
        if parsed.path != "/api/edge/tasks/fetch":
            self.send_error(404)
            return
        if parse_qs(parsed.query).get("sn") != [VALID_SN]:
            raise AssertionError("fetch used the wrong device SN")
        self.write_result(
            {
                "id": 77,
                "taskId": "task-77",
                "taskType": "ollama",
                "modelName": "qwen2.5:3b",
                "prompt": "contract test",
                "taskParams": "{}",
                "status": "running",
                "deviceSn": VALID_SN,
            }
        )


class AgentBackendContractTest(unittest.TestCase):
    def setUp(self):
        ContractHandler.submissions = []
        ContractHandler.reports = []
        ContractHandler.enrollments = []
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), ContractHandler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.temp = tempfile.TemporaryDirectory()
        state_dir = Path(self.temp.name)
        environment = {
            "ORIN_API_BASE_URL": f"http://127.0.0.1:{self.server.server_port}",
            "ORIN_STATE_DIR": str(state_dir),
            "ORIN_OUTBOX_DIR": str(state_dir / "outbox"),
            "ORIN_RUNTIME_DIR": str(state_dir / "runtime"),
            "ORIN_DEVICE_SN": VALID_SN,
            "ORIN_HARDWARE_FINGERPRINT": VALID_FINGERPRINT,
            "ORIN_IMAGE_LICENSE": VALID_LICENSE,
            "ORIN_REQUEST_RETRIES": "0",
        }
        self.environment = mock.patch.dict(os.environ, environment, clear=False)
        self.environment.start()
        spec = importlib.util.spec_from_file_location(f"orin_agent_contract_{id(self)}", AGENT_PATH)
        self.agent = importlib.util.module_from_spec(spec)
        assert spec and spec.loader
        spec.loader.exec_module(self.agent)

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        self.environment.stop()
        self.temp.cleanup()

    def test_enroll_report_fetch_execute_submit_contract(self):
        telemetry = {
            "sn": VALID_SN,
            "hardware_fingerprint": VALID_FINGERPRINT,
            "image_version": "orin-l4t-36.4.7-v1",
            "cpu_load": "1.0",
        }
        self.agent.ensure_enrolled(telemetry)
        heartbeat = self.agent.request("/api/edge/report", "POST", telemetry)
        with mock.patch.object(
            self.agent,
            "ollama_generate",
            return_value={"responseText": "contract-ok", "generateTokens": 5},
        ):
            self.assertTrue(self.agent.poll_task_once())

        self.assertEqual(30, self.agent.next_interval(heartbeat, 60))
        self.assertEqual(17, self.agent.next_task_interval(heartbeat, 60))
        self.assertEqual(VALID_LICENSE, ContractHandler.enrollments[0]["image_license"])
        self.assertEqual(VALID_SN, ContractHandler.reports[0]["sn"])
        self.assertEqual(1, len(ContractHandler.submissions))
        submitted = ContractHandler.submissions[0]
        self.assertEqual(77, submitted["id"])
        self.assertEqual(VALID_SN, submitted["deviceSn"])
        self.assertEqual("completed", submitted["status"])
        self.assertEqual("contract-ok", submitted["responseText"])
        self.assertEqual([], list(self.agent.OUTBOX_DIR.glob("*.json")))


if __name__ == "__main__":
    unittest.main()
