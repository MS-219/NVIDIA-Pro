/**
 * 聚芯 Orin小程序全局配置
 * 域名: orin-support.cn
 */

// API 基础地址
// 开发环境
// export const API_BASE = 'http://localhost:8090';

// 生产环境 - 使用主域名（证书支持）
export const API_BASE = 'https://orin-api.example.invalid';

// 备用 - 使用 IP（不需要 HTTPS）

// 文件上传地址
export const UPLOAD_URL = `${API_BASE}/api/upload/image`;

// 版本号
export const VERSION = '1.0.0';

// 应用名称
export const APP_NAME = '聚芯 Orin';

// 默认配置
export const config = {
    // 心跳间隔（毫秒）
    heartbeatInterval: 60000,

    // 请求超时时间（毫秒）
    requestTimeout: 10000,

    // 分页大小
    pageSize: 10,

    // 公告显示数量
    noticeLimit: 5
};

export default {
    API_BASE,
    UPLOAD_URL,
    VERSION,
    APP_NAME,
    config
};
