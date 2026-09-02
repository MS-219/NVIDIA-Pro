import { API_BASE_URL, appConfig } from './config';

export type ApiEnvelope<T> = {
  code: number;
  message?: string;
  msg?: string;
  data: T;
};

export type LoginPayload = {
  token: string;
  userId: number;
  phone: string;
  nickname: string;
};

export type SendCodePayload = {
  providerRequestId?: string;
  retryAfterSeconds: number;
};

export type DevicePayload = {
  id: number;
  code: string;
  name: string;
  status: string;
  hashrate: number;
  temperature: number | null;
  dailyEarnings: number;
  totalEarnings: number;
  lastReportedAt: string | null;
  boundAt: string | null;
};

export type DashboardPayload = {
  total: number;
  online: number;
  totalHashrate: number;
  todayEarnings: number;
  totalEarnings: number;
};

export type EarningsPayload = {
  items: Array<{
    deviceId: number;
    deviceName: string;
    todayEarnings: number;
    totalEarnings: number;
    updatedAt: string | null;
  }>;
  todayEarnings: number;
  totalEarnings: number;
};

const request = async <T>(path: string, init: RequestInit = {}, token?: string): Promise<T> => {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), appConfig.requestTimeoutMs);
  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      signal: controller.signal,
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(init.headers || {}),
      },
    });
    const raw = await response.text();
    let body: Partial<ApiEnvelope<T>> = {};
    if (raw) {
      try {
        body = JSON.parse(raw) as ApiEnvelope<T>;
      } catch {
        // Reverse proxies occasionally return HTML/plain text for 4xx/5xx.
      }
    }
    if (!response.ok || body.code !== 200) {
      const fallback = raw.replace(/\s+/g, ' ').trim().slice(0, 120);
      throw new Error(body.message || body.msg || fallback || `请求失败 (${response.status})`);
    }
    if (body.data === undefined) throw new Error('服务返回数据为空');
    return body.data as T;
  } catch (error) {
    if (typeof error === 'object' && error !== null && 'name' in error && error.name === 'AbortError') {
      throw new Error('请求超时，请检查网络后重试');
    }
    if (error instanceof TypeError) {
      throw new Error('无法连接服务器，请检查 API 地址和网络');
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
};

export const api = {
  sendLoginCode: (phone: string) =>
    request<SendCodePayload>('/api/auth/sms/send', {
      method: 'POST',
      body: JSON.stringify({ phone }),
    }),
  login: (phone: string, code: string) =>
    request<LoginPayload>('/api/auth/sms/login', {
      method: 'POST',
      body: JSON.stringify({ phone, code }),
    }),
  updateProfile: (token: string, nickname: string) =>
    request<LoginPayload>('/api/auth/me', {
      method: 'PATCH',
      body: JSON.stringify({ nickname }),
    }, token),
  listDevices: (token: string) =>
    request<DevicePayload[]>('/api/app/devices', {}, token),
  bindDevice: (token: string, code: string, name?: string) =>
    request<DevicePayload>('/api/app/devices/bind', {
      method: 'POST',
      body: JSON.stringify({ code, name: name?.trim() || undefined }),
    }, token),
  removeDevice: (token: string, id: number) =>
    request<void>(`/api/app/devices/${id}`, { method: 'DELETE' }, token),
  getDashboard: (token: string) =>
    request<DashboardPayload>('/api/app/dashboard/summary', {}, token),
  getEarnings: (token: string) =>
    request<EarningsPayload>('/api/app/earnings', {}, token),
};
