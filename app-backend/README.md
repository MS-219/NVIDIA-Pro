# Independent APP Backend

This service is intentionally separate from the legacy mini-program backend. It starts with a phone/SMS authentication vertical slice and has no data migration or OpenID dependency.

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
```

## APP device data

`app_node` is an APP-only table. It is deliberately not connected to the
legacy `device`, user, wallet, or earnings tables. Operations must provision a
row and its unique `binding_code` before a user can bind it; the mobile API
cannot create a node or supply `owner_user_id`.

For example, provision a fresh node directly in the new APP database (use a
random code in real deployments):

```sql
INSERT INTO app_node (binding_code, name, status)
VALUES ('ORIN-A1B2C3', '客厅节点', 'pending');
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

Wallet, withdrawal and exchange modules are intentionally not exposed yet. They will be added against this service's new schema and will not read the legacy database.
