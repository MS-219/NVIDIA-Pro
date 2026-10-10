# Remote terminal audit (2026-09-05)

Production evidence (read-only SSH `juxin@144.123.115.42:2222`):

- `juxin-node-backend` and `juxin-node-admin` healthy.
- DB device row: `sn=RK3588-F4B1609FBB0A6197`, `bind_code=JD0FA812`, `status=1`; heartbeat keeps updating.
- Admin issued terminal ticket successfully at 21:09:40 (`POST /api/admin/terminal/ticket/RK3588-F4B1609FBB0A6197`, status 200).
- Since backend start and through 21:13, backend log has **no** `设备终端通道已连接`/`管理员终端已连接` entries. Admin container nginx access log has no `/ws/...` request. Agent HTTP heartbeat/task polling works continuously.

Likely root cause: RK3588 agent's outbound WebSocket loop never reaches server (device-side process/log or TLS/DNS failure). The backend/frontend routing is internally correct: admin nginx `/ws/` proxies to `backend:8090`; outer nginx config currently proxies `/ws/` to `127.0.0.1:18175` (admin), which then proxies to backend.

Code checks:

- Agent `terminal_websocket_url()` builds `wss://jd.ldjuxin.yun/ws/device/<sn>` and sends `X-RK3588-Device-Token`.
- Backend interceptor accepts `/ws/device/(JD|RK3588)-[A-F0-9]{12,32}` and token auth; current SN `RK3588-F4B1609FBB0A6197` matches.
- Agent starts remote terminal thread at `start_remote_terminal()` and requires `python3-websocket`; shell defaults to user `juxin` (image inject script creates maintenance user).

Next diagnostic on device (via ADB):

```sh
ps -ef | grep rk3588_agent.py
tail -200 /var/log/juxin-rk3588-agent.log
python3 -c 'import websocket; print(websocket.__version__)'
cat /etc/juxin-rk3588/agent.env
curl -I https://jd.ldjuxin.yun/ws/device/RK3588-F4B1609FBB0A6197
getent passwd juxin; command -v setsid; command -v runuser
```

The key evidence is absence of any `/ws` access request while HTTP heartbeats continue; inspect agent log/process before changing code or production config.
