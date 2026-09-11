// Config plugin: 让 APP 能在应用内下载 APK 并调起系统安装器。
// 1. 添加 REQUEST_INSTALL_PACKAGES 权限（安装未知来源应用）。
// 2. 注册 androidx FileProvider，把缓存目录暴露给系统安装器（content:// URI）。
// 3. 写入 res/xml/file_paths.xml，声明可共享的路径（cache / external-cache / external-files）。
const { withAndroidManifest, withDangerousMod } = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

const FILE_PATHS_XML = `<?xml version="1.0" encoding="utf-8"?>
<paths>
  <cache-path name="cache" path="." />
  <external-cache-path name="external_cache" path="." />
  <external-files-path name="external_files" path="." />
</paths>
`;

function withApkInstall(config) {
  config = withAndroidManifest(config, (cfg) => {
    const manifest = cfg.modResults.manifest;

    // 1. 安装未知来源权限
    if (!Array.isArray(manifest['uses-permission'])) manifest['uses-permission'] = [];
    const hasPerm = manifest['uses-permission'].some(
      (p) => p.$ && p.$['android:name'] === 'android.permission.REQUEST_INSTALL_PACKAGES'
    );
    if (!hasPerm) {
      manifest['uses-permission'].push({ $: { 'android:name': 'android.permission.REQUEST_INSTALL_PACKAGES' } });
    }

    // 2. FileProvider
    const app = manifest.application && manifest.application[0];
    if (app) {
      if (!Array.isArray(app.provider)) app.provider = [];
      const hasProvider = app.provider.some(
        (p) => p.$ && p.$['android:authorities'] === '${applicationId}.fileprovider'
      );
      if (!hasProvider) {
        app.provider.push({
          $: {
            'android:name': 'androidx.core.content.FileProvider',
            'android:authorities': '${applicationId}.fileprovider',
            'android:exported': 'false',
            'android:grantUriPermissions': 'true',
          },
          'meta-data': [
            {
              $: {
                'android:name': 'android.support.FILE_PROVIDER_PATHS',
                'android:resource': '@xml/file_paths',
              },
            },
          ],
        });
      }
    }

    return cfg;
  });

  // 3. 写入 res/xml/file_paths.xml
  config = withDangerousMod(config, [
    'android',
    (cfg) => {
      const resXml = path.join(cfg.modRequest.platformProjectRoot, 'app', 'src', 'main', 'res', 'xml');
      fs.mkdirSync(resXml, { recursive: true });
      fs.writeFileSync(path.join(resXml, 'file_paths.xml'), FILE_PATHS_XML);
      return cfg;
    },
  ]);

  return config;
}

module.exports = withApkInstall;
