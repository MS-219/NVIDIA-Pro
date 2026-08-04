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
                "ORIN_API_BASE_URL": "https://nvidia.juxinsuanli.cn",
                "ORIN_RUNTIME_DIR": str(self.state_dir / "runtime"),
                "ORIN_OUTBOX_DIR": str(self.state_dir / "outbox"),
                "ORIN_REQUEST_RETRIES": "2",
                "ORIN_RETRY_BASE_SECONDS": "0.001",
                "ORIN_RECONNECT_INTERVAL": "5",
                "ORIN_NVPMODEL_CONFIG": str(self.state_dir / "nvpmodel.conf"),
                "ORIN_NVPMODEL_BIN": str(self.state_dir / "nvpmodel"),
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
            return_value={
                "code": 200,
                "data": {
                    "deviceSn": VALID_SN,
                    "bindCode": "Orin-A1B2C3",
                    "deviceToken": token,
                },
            }
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
        self.assertEqual("Orin-A1B2C3", self.agent.BIND_CODE_FILE.read_text().strip())
        mode = stat.S_IMODE(self.agent.TOKEN_FILE.stat().st_mode)
        self.assertEqual(0o600, mode)
        self.assertEqual(0o644, stat.S_IMODE(self.agent.BIND_CODE_FILE.stat().st_mode))
        args, kwargs = self.agent.request.call_args
        self.assertEqual("/api/edge/enroll", args[0])
        self.assertNotIn("image_license", args[2])
        self.assertEqual(VALID_SN, args[2]["sn"])
        self.assertEqual("orin-l4t-36.4.7-test", args[2]["image_version"])
        self.assertEqual(VALID_FINGERPRINT, args[2]["hardware_fingerprint"])
        self.assertEqual({"cpu_load": "12.5", "gpu_temperature": 42.0}, args[2]["telemetry"])
        self.assertFalse(kwargs["authenticated"])

    def test_enrollment_rejects_response_without_short_code(self):
        self.agent.request = mock.Mock(
            return_value={
                "code": 200,
                "data": {"deviceSn": VALID_SN, "deviceToken": "T" * 48},
            }
        )

        with self.assertRaisesRegex(self.agent.ApiError, "binding code"):
            self.agent.ensure_enrolled(
                {
                    "sn": VALID_SN,
                    "image_version": "orin-l4t-36.4.7-test",
                    "hardware_fingerprint": VALID_FINGERPRINT,
                }
            )

        self.assertFalse(self.agent.TOKEN_FILE.exists())

    def test_display_status_filters_secrets_and_prompts(self):
        task = {
            "id": 8,
            "taskId": "task-8",
            "taskType": "ollama",
            "modelName": "qwen2.5:3b",
            "prompt": "secret prompt",
            "deviceToken": "secret token",
        }

        self.agent.publish_display_status(
            phase="task",
            connected=True,
            telemetry={"gpu_usage": 72, "hardware_fingerprint": VALID_FINGERPRINT},
            task=self.agent.display_task(task, 1000),
        )

        status = json.loads(self.agent.DISPLAY_STATUS_FILE.read_text())
        self.assertEqual("task", status["phase"])
        self.assertEqual(72, status["telemetry"]["gpu_usage"])
        self.assertNotIn("hardware_fingerprint", status["telemetry"])
        self.assertNotIn("prompt", status["task"])
        self.assertNotIn("deviceToken", status["task"])

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
        self.assertEqual(5, self.agent.next_task_interval({"data": {"taskPollInterval": 1}}, 60))
        self.assertEqual(3600, self.agent.next_task_interval({"data": {"taskPollInterval": 9999}}, 60))

    def test_backend_runtime_config_is_published_for_the_display(self):
        response = {
            "data": {
                "heartbeatInterval": 90,
                "taskPollInterval": 15,
                "offlineThreshold": 240,
                "powerMode": "25W",
            }
        }

        with mock.patch.object(self.agent, "apply_power_mode") as apply_power_mode:
            intervals = self.agent.apply_runtime_config(response, 60, 60, 180)

        self.assertEqual((90, 15, 240), intervals)
        self.assertEqual(
            {
                "heartbeatInterval": 90,
                "taskPollInterval": 15,
                "offlineThreshold": 240,
                "powerMode": "25W",
            },
            self.agent.DISPLAY_STATE["runtimeConfig"],
        )
        apply_power_mode.assert_called_once_with("25W")

    def test_power_mode_names_are_parsed_from_device_config(self):
        config = """
< POWER_MODEL ID=0 NAME=15W >
< POWER_MODEL ID=1 NAME=25W >
< POWER_MODEL ID=2 NAME=MAXN_SUPER >
"""

        self.assertEqual(
            {"15W": 0, "25W": 1, "MAXN_SUPER": 2},
            self.agent.parse_power_modes(config),
        )

    def test_tegrastats_timeout_output_is_parsed(self):
        completed = mock.Mock(
            returncode=124,
            stdout=(
                "RAM 620/7619MB (lfb 1100x4MB) GR3D_FREQ 37% "
                "CPU@39.5C gpu@42.25C tj@43.0C VDD_IN 5520mW/5400mW\n"
            ),
            stderr="",
        )

        with mock.patch.object(self.agent.subprocess, "run", return_value=completed):
            metrics = self.agent.tegra_metrics()

        self.assertEqual(37.0, metrics["gpu_usage"])
        self.assertEqual(42.25, metrics["gpu_temperature"])
        self.assertEqual(5.52, metrics["power_watts"])

    def test_display_utilization_matches_product_ranges(self):
        first = self.agent.display_utilization_metrics(120.0)
        second = self.agent.display_utilization_metrics(120.0)

        self.assertEqual(first, second)
        self.assertGreaterEqual(first["cpu_load"], 15)
        self.assertLessEqual(first["cpu_load"], 30)
        self.assertGreaterEqual(first["mem_load"], 28)
        self.assertLessEqual(first["mem_load"], 42)
        self.assertGreaterEqual(first["gpu_usage"], 60)
        self.assertLessEqual(first["gpu_usage"], 75)

    def test_network_throughput_uses_real_interface_byte_counters(self):
        self.agent.NETWORK_SAMPLE = {}
        with mock.patch.object(self.agent, "default_network_interface", return_value="eth0"), \
             mock.patch.object(
                 self.agent,
                 "interface_counters",
                 side_effect=[(1_000_000, 2_000_000), (1_250_000, 2_500_000)],
             ), \
             mock.patch.object(self.agent.time, "monotonic", side_effect=[10.0, 12.0]), \
             mock.patch.object(self.agent.time, "sleep"):
            metrics = self.agent.network_throughput()

        self.assertEqual("eth0", metrics["network_interface"])
        self.assertEqual(1.0, metrics["network_download_mbps"])
        self.assertEqual(2.0, metrics["network_upload_mbps"])

    def test_network_quality_parses_real_ping_latency_and_packet_loss(self):
        ping = mock.Mock(
            returncode=0,
            stdout=(
                "3 packets transmitted, 3 received, 0% packet loss, time 2002ms\n"
                "rtt min/avg/max/mdev = 7.200/8.450/9.700/0.800 ms\n"
            ),
            stderr="",
        )
        with mock.patch.object(self.agent.subprocess, "run", return_value=ping) as run:
            metrics = self.agent.network_quality()

        self.assertEqual(8.45, metrics["network_latency_ms"])
        self.assertEqual(0.0, metrics["network_packet_loss_percent"])
        ping_args = run.call_args.args[0]
        self.assertEqual("nvidia.juxinsuanli.cn", ping_args[-1])

    def test_power_mode_uses_device_local_id_and_persists_status(self):
        self.agent.NVPMODEL_CONFIG.write_text(
            "< POWER_MODEL ID=0 NAME=15W >\n"
            "< POWER_MODEL ID=1 NAME=25W >\n"
            "< POWER_MODEL ID=2 NAME=MAXN_SUPER >\n"
        )
        query = mock.Mock(returncode=0, stdout="NV Power Mode: MAXN_SUPER\n2\n", stderr="")
        applied = mock.Mock(returncode=0, stdout="", stderr="")

        with mock.patch.object(self.agent.subprocess, "run", side_effect=[query, applied]) as run:
            status = self.agent.apply_power_mode("25W")

        self.assertEqual("25W", status["current"])
        self.assertEqual("applied", status["applyStatus"])
        self.assertEqual(
            [str(self.agent.NVPMODEL_BIN), "-m", "1"],
            run.call_args_list[1].args[0],
        )
        persisted = json.loads(self.agent.POWER_MODE_STATE_FILE.read_text())
        self.assertEqual("25W", persisted["target"])

    def test_power_mode_is_not_reapplied_when_already_active(self):
        self.agent.NVPMODEL_CONFIG.write_text("< POWER_MODEL ID=1 NAME=25W >\n")
        query = mock.Mock(returncode=0, stdout="NV Power Mode: 25W\n1\n", stderr="")

        with mock.patch.object(self.agent.subprocess, "run", return_value=query) as run:
            status = self.agent.apply_power_mode("25W")

        self.assertEqual("unchanged", status["applyStatus"])
        run.assert_called_once()

    def test_unsupported_power_mode_preserves_current_mode(self):
        self.agent.NVPMODEL_CONFIG.write_text("< POWER_MODEL ID=2 NAME=MAXN_SUPER >\n")
        query = mock.Mock(returncode=0, stdout="NV Power Mode: MAXN_SUPER\n2\n", stderr="")

        with mock.patch.object(self.agent.subprocess, "run", return_value=query) as run:
            status = self.agent.apply_power_mode("25W")

        self.assertEqual("MAXN_SUPER", status["current"])
        self.assertEqual("error", status["applyStatus"])
        self.assertIn("not supported", status["error"])
        run.assert_called_once()

    def test_power_mode_failure_is_reported_without_raising(self):
        self.agent.NVPMODEL_CONFIG.write_text("< POWER_MODEL ID=1 NAME=25W >\n")
        query = mock.Mock(returncode=0, stdout="NV Power Mode: MAXN_SUPER\n2\n", stderr="")
        failed = mock.Mock(returncode=1, stdout="", stderr="mode rejected")

        with mock.patch.object(self.agent.subprocess, "run", side_effect=[query, failed]):
            status = self.agent.apply_power_mode("25W")

        self.assertEqual("MAXN_SUPER", status["current"])
        self.assertEqual("error", status["applyStatus"])
        self.assertIn("mode rejected", status["error"])

    def test_power_mode_runtime_failure_does_not_break_heartbeat_config(self):
        response = {
            "data": {
                "heartbeatInterval": 90,
                "taskPollInterval": 15,
                "offlineThreshold": 240,
                "powerMode": "25W",
            }
        }

        with mock.patch.object(self.agent, "apply_power_mode", side_effect=OSError("read only")):
            intervals = self.agent.apply_runtime_config(response, 60, 60, 180)

        self.assertEqual((90, 15, 240), intervals)
        self.assertEqual("error", self.agent.DISPLAY_STATE["telemetry"]["power_mode_apply_status"])
        self.assertIn("read only", self.agent.DISPLAY_STATE["telemetry"]["power_mode_error"])

    def test_terminal_websocket_url_uses_secure_backend_scheme(self):
        self.agent.API_BASE = "https://nvidia.juxinsuanli.cn"

        self.assertEqual(
            f"wss://nvidia.juxinsuanli.cn/ws/device/{VALID_SN}",
            self.agent.terminal_websocket_url(),
        )

    def test_terminal_protocol_controls_maintenance_shell(self):
        shell = mock.Mock()

        self.agent.handle_terminal_message(
            json.dumps({"type": "open", "cols": 100, "rows": 30}), shell
        )
        self.agent.handle_terminal_message(
            json.dumps({"type": "input", "data": "id\r"}), shell
        )
        self.agent.handle_terminal_message(
            json.dumps({"type": "resize", "cols": 120, "rows": 40}), shell
        )
        self.agent.handle_terminal_message(json.dumps({"type": "close"}), shell)

        shell.open.assert_called_once_with(100, 30)
        shell.write.assert_called_once_with("id\r")
        shell.resize.assert_called_once_with(120, 40)
        shell.close.assert_called_once()

    def test_terminal_shell_uses_a_controlling_pty_session(self):
        shell = self.agent.TerminalShell(mock.Mock())
        account = self.agent.subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout="juxin:x:1000:1000::/home/juxin:/bin/bash\n",
            stderr="",
        )
        process = mock.Mock(pid=4321)
        reader = mock.Mock()

        with mock.patch.object(self.agent.subprocess, "run", return_value=account), \
                mock.patch.object(self.agent.os, "access", return_value=True), \
                mock.patch.object(self.agent.pty, "openpty", return_value=(10, 11)), \
                mock.patch.object(self.agent.subprocess, "Popen", return_value=process) as popen, \
                mock.patch.object(self.agent.os, "close"), \
                mock.patch.object(self.agent.threading, "Thread", return_value=reader), \
                mock.patch.object(shell, "resize"):
            shell.open(120, 40)

        command, = popen.call_args.args
        self.assertEqual(
            [
                self.agent.TERMINAL_SETSID_BIN,
                "--ctty",
                "/usr/sbin/runuser",
                "-u",
                "juxin",
                "--",
                "/bin/bash",
                "--login",
            ],
            command,
        )
        self.assertNotIn("start_new_session", popen.call_args.kwargs)
        self.assertEqual(11, popen.call_args.kwargs["stdin"])
        self.assertEqual(11, popen.call_args.kwargs["stdout"])
        self.assertEqual(11, popen.call_args.kwargs["stderr"])
        reader.start.assert_called_once()

    def test_terminal_connection_authenticates_with_device_token(self):
        self.agent.atomic_write_secret(self.agent.TOKEN_FILE, "terminal-device-token")
        connection = mock.Mock()
        connection.recv.side_effect = [
            json.dumps({"type": "open", "cols": 80, "rows": 24}),
            json.dumps({"type": "close"}),
            "",
        ]
        websocket_module = mock.Mock()
        websocket_module.create_connection.return_value = connection
        shell = mock.Mock()

        with mock.patch.object(self.agent, "TerminalShell", return_value=shell):
            self.agent.run_terminal_connection(websocket_module)

        websocket_module.create_connection.assert_called_once()
        _, kwargs = websocket_module.create_connection.call_args
        self.assertEqual(["X-Orin-Device-Token: terminal-device-token"], kwargs["header"])
        shell.open.assert_called_once_with(80, 24)
        shell.close.assert_called()
        sent = [json.loads(call.args[0]) for call in connection.send.call_args_list]
        self.assertIn({"type": "status", "status": "ready"}, sent)

    def test_terminal_keepalive_sends_websocket_ping(self):
        connection = mock.Mock()
        stop = mock.Mock()
        stop.wait.side_effect = [False, True]

        self.agent.terminal_keepalive(connection, mock.MagicMock(), stop)

        connection.ping.assert_called_once_with("juxin-orin")
        stop.wait.assert_called_with(self.agent.TERMINAL_KEEPALIVE_INTERVAL)

    def test_failed_backend_attempt_uses_short_reconnect_interval(self):
        self.assertEqual(5, self.agent.next_attempt_delay(False, 60))
        self.assertEqual(60, self.agent.next_attempt_delay(True, 60))
        self.assertEqual(5, self.agent.next_attempt_delay(False, 10))

    def test_successful_backend_retry_restores_online_display(self):
        self.agent.DISPLAY_STATE.update(
            {"phase": "offline", "connected": False, "error": "network timeout"}
        )

        self.agent.publish_connection_recovered()

        status = json.loads(self.agent.DISPLAY_STATUS_FILE.read_text())
        self.assertEqual("idle", status["phase"])
        self.assertTrue(status["connected"])
        self.assertEqual("", status["error"])

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
        status = json.loads(self.agent.DISPLAY_STATUS_FILE.read_text())
        self.assertEqual("completed", status["phase"])
        self.assertEqual("completed", status["task"]["status"])

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
