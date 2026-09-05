# 聚芯节点后台（二开副本）

这是从仓库原 `admin/` 与 `backend/` 复制出的独立二开目录。原目录不在本目录内，也不会被此项目的构建或部署命令修改。

- `admin/`：原 Vue 管理后台的副本，已改为聚芯节点/RK3588 品牌。
- `backend/`：原 Spring Boot 后端的副本，增加 RK3588 设备令牌、SN 和 WebSocket 兼容。
- `docker-compose.yml`：独立容器、端口和 MySQL 数据卷，避免碰到原 NVIDIA 服务。

服务器部署前复制 `.env.example` 为 `.env` 并填写随机密码。生产域名的 Nginx 只需反代到本目录的 admin 端口（默认 `127.0.0.1:18175`）。

## 服务器部署

以下命令在服务器上执行，目录以 `/www/wwwroot/NVIDIA-Pro` 为例。它只创建
`juxin-node-*` 容器和 `juxin-node-mysql-data` 数据卷，不会停止或删除原项目容器。

```bash
cd /www/wwwroot/NVIDIA-Pro/juxin-node
cp .env.example .env
DB_PASS="$(openssl rand -hex 24)"
ROOT_PASS="$(openssl rand -hex 24)"
JWT_SECRET="$(openssl rand -hex 32)"
ADMIN_PASS="$(openssl rand -hex 24)"
sed -i \
  -e "s#^JUXIN_NODE_DB_PASSWORD=.*#JUXIN_NODE_DB_PASSWORD=$DB_PASS#" \
  -e "s#^JUXIN_NODE_MYSQL_ROOT_PASSWORD=.*#JUXIN_NODE_MYSQL_ROOT_PASSWORD=$ROOT_PASS#" \
  -e "s#^JUXIN_NODE_JWT_SECRET=.*#JUXIN_NODE_JWT_SECRET=$JWT_SECRET#" \
  -e "s#^JUXIN_NODE_ADMIN_PASSWORD=.*#JUXIN_NODE_ADMIN_PASSWORD=$ADMIN_PASS#" \
  .env
# 按需修改 JUXIN_NODE_ADMIN_USERNAME/JUXIN_NODE_ADMIN_PASSWORD；不要把 .env 提交到 Git。
docker compose --env-file .env up -d --build mysql backend admin
docker compose --env-file .env ps
curl -fsS http://127.0.0.1:18092/api/health
```

将 `deploy/nginx-jd.ldjuxin.yun.conf` 安装为 Nginx 配置前，先备份服务器上现有的
`jd.ldjuxin.yun` 配置，并确保旧域名站点不会与它同时声明相同的
`server_name`。示例：

```bash
sudo cp /etc/nginx/sites-available/jd.ldjuxin.yun \
  /etc/nginx/sites-available/jd.ldjuxin.yun.bak.$(date +%Y%m%d%H%M%S)
sudo cp deploy/nginx-jd.ldjuxin.yun.conf \
  /etc/nginx/sites-available/jd.ldjuxin.yun
sudo nginx -t && sudo systemctl reload nginx
curl -kfsS https://jd.ldjuxin.yun/ | grep -m1 '<title>'
```

如果证书路径不是 `/etc/letsencrypt/live/jd.ldjuxin.yun/`，先在该 Nginx 文件中调整
`ssl_certificate` 和 `ssl_certificate_key`。后台登录账号密码由 `.env` 中的
`JUXIN_NODE_ADMIN_USERNAME`、`JUXIN_NODE_ADMIN_PASSWORD` 决定。

## 从旧 HTML 控制台切换

`website/console.html` 是早期静态验证页，不是二开后台。生产域名必须使用本目录的
`deploy/nginx-jd.ldjuxin.yun.conf`，并让 `juxin-node-admin` 容器提供页面。

如果域名现在仍显示 `console.html`，在服务器执行：

```bash
cd /www/wwwroot/NVIDIA-Pro/juxin-node
docker compose --env-file .env up -d --build mysql backend admin
curl -fsS http://127.0.0.1:18092/api/health
curl -fsS http://127.0.0.1:18175/ | grep -m1 '<title>'

sudo cp /etc/nginx/sites-available/jd.ldjuxin.yun \
  /etc/nginx/sites-available/jd.ldjuxin.yun.bak.$(date +%Y%m%d%H%M%S)
sudo cp deploy/nginx-jd.ldjuxin.yun.conf \
  /etc/nginx/sites-available/jd.ldjuxin.yun
sudo ln -sfn /etc/nginx/sites-available/jd.ldjuxin.yun \
  /etc/nginx/sites-enabled/jd.ldjuxin.yun
sudo nginx -t && sudo systemctl reload nginx
curl -kfsS https://jd.ldjuxin.yun/ | grep -m1 '<title>'
```

最后一条应返回 `聚芯节点｜设备运营后台`，页面源码应包含 `<div id="app">`。
如果仍返回 `console.html`，用 `sudo nginx -T | grep -n -B3 -A8
'server_name jd.ldjuxin.yun'` 找出并停用同一域名的其它 server block 后再重载 Nginx。

后台创建的虚拟设备会写入 `juxin_node.device`。如果 APP 后端配置了
`APP_NODE_DB_URL`、`APP_NODE_DB_USERNAME` 和 `APP_NODE_DB_PASSWORD`，APP
刷新设备列表时会按手机号把这些虚拟设备同步到 `orin_app.app_node`，因此
后台指定用户后无需在 APP 再次输入绑定码。

## 与原项目的边界

`juxin-node/admin` 和 `juxin-node/backend` 是已复制后的独立源码；构建、数据库和端口
均与原 `admin`/`backend` 隔离。Java 后端仍保留原项目使用的 `ORIN_*` 环境变量名，
这是兼容配置键，不代表页面或设备品牌。页面只显示“聚芯节点 / RK3588”，不提供功耗模式
设置，也不引用原 NVIDIA 图标或 token 文件。
