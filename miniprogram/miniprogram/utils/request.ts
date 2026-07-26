/**
 * 统一请求工具 - 处理 token 过期自动登出
 */
import { API_BASE } from '../config';

interface RequestOptions {
    url: string;
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
    data?: any;
    header?: any;
    needAuth?: boolean; // 是否需要认证
}

interface RequestResult<T = any> {
    code: number;
    msg?: string;
    data?: T;
}

/**
 * 检查响应是否为 token 过期（导出供全局使用）
 */
export function isTokenExpired(res: any): boolean {
    if (!res || !res.data) return false;

    const { code, msg } = res.data;

    // 仅 401 才判为 token 过期，403 是权限不足不应清除登录态
    if (code === 401) return true;

    // 消息匹配收紧：必须明确是登录过期相关
    if (msg && typeof msg === 'string') {
        // 必须同时包含"登录"+"过期/失效" 或 明确的 token 过期提示
        if ((msg.includes('登录') && (msg.includes('过期') || msg.includes('失效'))) ||
            msg === '请重新登录' ||
            msg.includes('token已过期') ||
            msg.includes('Token已过期')) {
            return true;
        }
    }

    return false;
}

/**
 * 处理 token 过期 - 清除登录状态并跳转（导出供全局使用）
 */
export function handleTokenExpired() {
    // 清除所有登录相关的存储
    wx.removeStorageSync('token');
    wx.removeStorageSync('userId');
    wx.removeStorageSync('userInfo');
    wx.removeStorageSync('openid');

    // 静默处理：不弹窗，由页面自身检测登录状态并展示登出 UI
    // "我的"页面的 verifyLoginStatus 会自动检测到 token 失效并切换为登出状态
}

/**
 * 统一请求方法
 */
export function request<T = any>(options: RequestOptions): Promise<RequestResult<T>> {
    return new Promise((resolve, reject) => {
        const token = wx.getStorageSync('token') || '';

        // 构建请求头
        const header: any = {
            'Content-Type': 'application/json',
            ...options.header
        };

        // 如果有 token，添加到请求头
        if (token) {
            header['Authorization'] = `Bearer ${token}`;
        }

        wx.request({
            url: options.url.startsWith('http') ? options.url : `${API_BASE}${options.url}`,
            method: options.method || 'GET',
            data: options.data,
            header,
            success: (res: any) => {
                // 检查是否 token 过期
                if (isTokenExpired(res)) {
                    handleTokenExpired();
                    reject({ code: 401, msg: '登录已过期' });
                    return;
                }

                resolve(res.data);
            },
            fail: (err) => {
                wx.showToast({ title: '网络错误', icon: 'none' });
                reject(err);
            }
        });
    });
}

/**
 * 检查登录状态（可在需要的地方调用）
 */
export function checkLoginStatus(): boolean {
    const userId = wx.getStorageSync('userId');
    const token = wx.getStorageSync('token');
    return !!(userId && token);
}

/**
 * 退出登录
 */
export function logout() {
    wx.removeStorageSync('token');
    wx.removeStorageSync('userId');
    wx.removeStorageSync('userInfo');
    wx.removeStorageSync('openid');

    wx.showToast({ title: '已退出登录', icon: 'success' });

    // 延迟跳转
    setTimeout(() => {
        wx.switchTab({ url: '/pages/my/my' });
    }, 500);
}

export default {
    request,
    checkLoginStatus,
    logout,
    API_BASE
};
