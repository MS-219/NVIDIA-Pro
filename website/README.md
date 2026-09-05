# 聚芯节点官网

这是一个独立静态官网。官网根路径展示产品介绍，`/api/` 由 Nginx 转发到独立 APP 后端，`/downloads/juxin-node-latest.apk` 提供 APP 安装包。

本目录中的 `console.html`、`console.js` 和 `console.css` 只是早期静态验证页，不是生产管理后台。
生产后台请部署 [juxin-node/README.md](../juxin-node/README.md) 中的 Vue 二开前端和独立后端。

## 本地预览

```bash
cd website
python3 -m http.server 4173
```

打开 <http://127.0.0.1:4173>。下载按钮需要把 APK 放到 `website/downloads/juxin-node-latest.apk` 后才会有实际文件。

## 服务器部署（仅官网）

仅在需要发布官网和 APP 下载页时，将本目录部署到 `/var/www/juxin-node-site`。不要用本目录的
`nginx-jd.ldjuxin.yun.conf` 覆盖生产管理后台域名；它会把根路径指向早期的 `console.html`。

```bash
sudo mkdir -p /var/www/juxin-node-site/downloads
sudo rsync -a --delete website/ /var/www/juxin-node-site/
```

将最终 APK 上传到 `/var/www/juxin-node-site/downloads/juxin-node-latest.apk`，官网的“立即下载”按钮即可生效。
