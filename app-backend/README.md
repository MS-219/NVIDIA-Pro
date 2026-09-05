# Independent APP Backend

This service keeps APP accounts and counters in its own database. When the
optional legacy database connection is configured, virtual devices assigned in
the二开后台 are mirrored into the APP node table by matching the user's phone
number.

## Run locally

For Docker Compose, create a local `.env` from `.env.example` and replace every
`replace-with-*` value before starting. The file is ignored by Git and must not
be committed:

```bash
cp .env.example .env
vi .env
docker compose up -d --build
```

At minimum, Compose requires `APP_DB_PASSWORD`, `APP_MYSQL_ROOT_PASSWORD`,
`APP_JWT_SECRET`, and `APP_SMS_PEPPER`. Production also requires the Alibaba
Cloud variables and `APP_SMS_PROVIDER=aliyun`.

For a direct Maven run, create a MySQL database named `orin_app` and set the
`APP_DB_*` variables, then run `mvn spring-boot:run` from this directory.

For a self-contained local smoke test (no MySQL or Alibaba credentials), run
`mvn spring-boot:run -Dspring-boot.run.profiles=local`. This uses a local H2
file database and the mock SMS gateway; it is not a production configuration.

For the browser preview, allow the local Expo origins when starting the
backend, for example `APP_CORS_ALLOWED_ORIGINS=http://localhost:8088,http://127.0.0.1:8088`.
The Compose file defaults to the mock SMS gateway for local use; set
`APP_SMS_PROVIDER=aliyun` and all Alibaba Cloud variables only in a production
environment.

The mock provider writes the generated code to the server log and never returns it over HTTP. Production must use `APP_SMS_PROVIDER=aliyun`.

## API

```text
POST /api/auth/sms/send  {"phone":"13800138000"}
POST /api/auth/sms/login {"phone":"13800138000","code":"123456"}
GET  /api/auth/me        Authorization: Bearer <token>
PATCH /api/auth/me       {"nickname":"新昵称"}  Authorization: Bearer <token>
GET  /api/app/devices   Authorization: Bearer <token>
POST /api/app/devices/bind  Authorization: Bearer <token>
                         {"code":"ORIN-A1B2C3","name":"客厅节点"}
DELETE /api/app/devices/{id}  Authorization: Bearer <token>
GET  /api/app/dashboard/summary  Authorization: Bearer <token>
GET  /api/app/earnings  Authorization: Bearer <token>
GET  /api/health

# Unified APP and RK3588 operations console (APP_ADMIN_USERNAME/PASSWORD)
POST /api/admin/login         {"username":"admin","password":"..."}
GET  /api/admin/me            Authorization: Bearer <admin-token>
GET  /api/admin/edge/devices  Authorization: Bearer <admin-token>
GET  /api/admin/edge/commands Authorization: Bearer <admin-token>
POST /api/admin/edge/devices/{sn}/commands
                              {"commandType":"HEALTH_CHECK"}
POST /api/admin/edge/terminal/ticket/{sn}
GET  /ws/admin/terminal/{sn}?ticket=<one-time-ticket>

# APP management modules (admin token required)
GET /api/admin/overview/summary
GET/PATCH /api/admin/users[/{id}]
GET /api/admin/earnings
GET /api/admin/wallet/ledger; POST /api/admin/wallet/adjust
GET /api/admin/withdrawals; POST /api/admin/withdrawals/{id}/approve|reject
GET /api/admin/payment-applies; POST /api/admin/payment-applies/{id}/approve|reject
GET/POST/PATCH /api/admin/notices[/{id}]
GET /api/admin/feedback; POST /api/admin/feedback/{id}/reply
GET /api/admin/teams; GET /api/admin/rewards
GET/POST/PATCH /api/admin/exchange/products[/{id}]
GET /api/admin/exchange/orders; POST /api/admin/exchange/orders/{id}/ship|cancel
GET/POST /api/admin/tasks; GET/POST /api/admin/upgrades/packages
GET /api/admin/settings; PUT /api/admin/settings/{key}

# RK3588S device protocol (X-RK3588-Device-Token)
POST /api/edge/enroll      {\"sn\":\"RK3588-...\",\"agentVersion\":\"...\",\"imageVersion\":\"...\"}
POST /api/edge/report      device telemetry JSON
GET  /api/edge/tasks/fetch
POST /api/edge/tasks/submit device task result JSON
```

## APP device data

`app_node` is the APP-facing node table. An RK3588S enrollment creates the
pending row and its unique binding code; a user can then claim it from the APP.
When `APP_NODE_DB_URL`, `APP_NODE_DB_USERNAME`, and `APP_NODE_DB_PASSWORD` are
set, each authenticated device refresh also imports type-1 virtual devices
from the二开后台 by matching phone number and assigns the verified APP account
as owner. The mobile API still cannot create an arbitrary node or supply
`owner_user_id`.

For example, provision a fresh node directly in the new APP database (use a
random code in real deployments):

```sql
INSERT INTO app_node (binding_code, name, status)
VALUES ('RK3588-A1B2C3', '客厅节点', 'pending');
```

`POST /api/app/devices/bind` derives the owner from the verified Bearer JWT.
An unknown code returns `404`, and a code that has already been claimed returns
`409`. `DELETE /api/app/devices/{id}` releases the relationship and leaves the
provisioned row available for a later account. Device counters are stored on
the APP node row for this first vertical slice; `/api/app/earnings` returns
per-node snapshots plus `todayEarnings` and `totalEarnings` and may be empty
until a node reports data.

Alibaba Cloud credentials stay on the server. Use a RAM sub-account restricted to SMS sending and rotate the keys independently of the app release.
The SDK uses bounded network timeouts by default (connect 5s, read 10s); tune
`ALI_SMS_CONNECT_TIMEOUT_MILLIS` and `ALI_SMS_READ_TIMEOUT_MILLIS` when needed.

The `website/console.*` files are an early static verification console and are
not the production operations UI. Production administration is served by the
separate Vue fork in `juxin-node/admin`, with APIs from `juxin-node/backend`.
Do not install `website/nginx-jd.ldjuxin.yun.conf` for the production admin
domain; use `juxin-node/deploy/nginx-jd.ldjuxin.yun.conf` instead. The APP
backend remains the service for mobile authentication and APP APIs.

The APP client can read published notices, submit feedback, view its wallet,
request withdrawals, submit payment accounts, view invites and place exchange
orders through the corresponding `/api/app/*` endpoints. Wallet, withdrawal
and exchange data use only the APP tables in this service. Financial mutations
are ledger entries and administrator writes are recorded in
`app_admin_audit_log`; the legacy database is not read.
