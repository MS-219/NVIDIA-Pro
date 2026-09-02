# 聚芯节点官网

这是一个独立静态官网。官网根路径展示产品介绍，`/api/` 由 Nginx 转发到独立 APP 后端，`/downloads/juxin-node-latest.apk` 提供 APP 安装包。

## 本地预览

```bash
cd website
python3 -m http.server 4173
```

打开 <http://127.0.0.1:4173>。下载按钮需要把 APK 放到 `website/downloads/juxin-node-latest.apk` 后才会有实际文件。

## 服务器部署

建议将本目录部署到 `/var/www/juxin-node-site`，并让 Nginx 使用 `nginx-jd.ldjuxin.yun.conf`：

```bash
sudo mkdir -p /var/www/juxin-node-site/downloads
sudo rsync -a --delete website/ /var/www/juxin-node-site/
sudo cp website/nginx-jd.ldjuxin.yun.conf /etc/nginx/sites-available/jd.ldjuxin.yun
sudo ln -sfn /etc/nginx/sites-available/jd.ldjuxin.yun /etc/nginx/sites-enabled/jd.ldjuxin.yun
sudo nginx -t && sudo systemctl reload nginx
```

将最终 APK 上传到 `/var/www/juxin-node-site/downloads/juxin-node-latest.apk`，官网的“立即下载”按钮即可生效。
