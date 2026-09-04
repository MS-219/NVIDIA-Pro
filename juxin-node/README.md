# 聚芯节点后台（二开副本）

这是从仓库原 `admin/` 与 `backend/` 复制出的独立二开目录。原目录不在本目录内，也不会被此项目的构建或部署命令修改。

- `admin/`：原 Vue 管理后台的副本，已改为聚芯节点/RK3588 品牌。
- `backend/`：原 Spring Boot 后端的副本，增加 RK3588 设备令牌、SN 和 WebSocket 兼容。
- `docker-compose.yml`：独立容器、端口和 MySQL 数据卷，避免碰到原 NVIDIA 服务。

服务器部署前复制 `.env.example` 为 `.env` 并填写随机密码。生产域名的 Nginx 只需反代到本目录的 admin 端口（默认 `127.0.0.1:18175`）。
