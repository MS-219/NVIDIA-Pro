# Juxin Orin Platform

独立的 Jetson Orin Nano 算力节点产品仓库。该仓库从旧 LD-AI 项目的业务能力起步，但不共享代码、数据库、部署配置、微信 AppID、设备身份或升级通道。

## Modules

- `image/`: Jetson L4T 36.4.x 系统定制、ARM64 节点 Agent 和镜像构建说明。
- `backend/`: 独立 Spring Boot API，默认端口 `8090`，独立数据库 `juxin_orin`。
- `admin/`: Orin GPU 节点运营控制台。
- `miniprogram/`: 独立微信小程序源码，发布前必须配置新的 AppID 和 API 域名。
- `deploy/`: 独立部署配置。

## Local start

```bash
cp .env.example .env
docker compose up -d --build

cd admin
npm install
npm run dev
```

后台前端默认请求同源 `/api`。本地开发由 Vite 将请求代理到 `http://127.0.0.1:8090`。

首次部署前需要按顺序初始化独立数据库：先执行 `backend/sql/orin_base.sql`，再执行当前功能所需的增量 SQL。首个管理员不会写入 SQL，必须通过 `.env` 中的 `ORIN_ADMIN_USERNAME` 和 `ORIN_ADMIN_PASSWORD` 创建。

## Isolation rules

1. 禁止复制旧项目的 `.env`、证书、微信密钥和第三方密钥。
2. 节点编号使用 `ORIN-` 前缀，首次启动生成独立身份。
3. 仅接受 `aarch64` 且 L4T 版本匹配的升级包。
4. 模型文件挂载到 `/opt/juxin-orin/models`，不写入系统镜像和 Git。
5. 旧项目与本项目不共用数据库、Redis、MQTT、域名、容器或发布流水线。
