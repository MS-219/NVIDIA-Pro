# Juxin Orin Platform

独立的 Jetson Orin Nano 算力节点产品仓库。该仓库从旧 LD-AI 项目的业务能力起步，但不共享代码、数据库、部署配置、微信 AppID、设备身份或升级通道。

## Modules

- `image/`: Jetson L4T 36.4.x 系统定制、ARM64 节点 Agent 和镜像构建说明。
- `backend/`: 独立 Spring Boot API，容器内部端口 `8090`，独立数据库 `juxin_orin`。
- `admin/`: Orin GPU 节点运营控制台。
- `miniprogram/`: 独立微信小程序源码，发布前必须配置新的 AppID 和 API 域名。
- `deploy/`: 独立部署配置。

## Docker deployment

```bash
cp .env.example .env
# Edit .env and replace every placeholder with an independent secret.
./deploy-docker.sh up
```

The first start of a new MySQL volume automatically runs:

- `deploy/mysql-init/001-schema.sql`: the complete independent schema.
- `deploy/mysql-init/002-default-settings.sql`: Orin platform defaults.

No default administrator password is stored in SQL. The backend creates the first administrator from `ORIN_ADMIN_USERNAME` and `ORIN_ADMIN_PASSWORD`; the password must contain at least 12 characters.

After startup:

- Admin console: `http://SERVER_IP:18174`
- Backend health: `http://SERVER_IP:18090/api/health`

The host ports are isolated from the old project and configurable in `.env`:

```dotenv
ORIN_BACKEND_PORT=18090
ORIN_ADMIN_PORT=18174
```

Common operations:

```bash
./deploy-docker.sh status
./deploy-docker.sh logs 200
./deploy-docker.sh restart
./deploy-docker.sh down
```

MySQL initialization files only run when the data volume is empty. Never delete the production volume just to rerun initialization; use a reviewed migration for an existing database.

## Frontend development

```bash
cd admin
npm install
npm run dev
```

后台前端默认请求同源 `/api`。本地开发由 Vite 将请求代理到 `http://127.0.0.1:8090`。

新系统不包含旧小程序的 AI 创作功能。`compute_job` 仅用于后台向 Orin 节点下发推理或计算作业，并记录设备执行结果。

## Isolation rules

1. 禁止复制旧项目的 `.env`、证书、微信密钥和第三方密钥。
2. 节点编号使用 `ORIN-` 前缀，首次启动生成独立身份。
3. 仅接受 `aarch64` 且 L4T 版本匹配的升级包。
4. 模型文件挂载到 `/opt/juxin-orin/models`，不写入系统镜像和 Git。
5. 旧项目与本项目不共用数据库、Redis、MQTT、域名、容器或发布流水线。
