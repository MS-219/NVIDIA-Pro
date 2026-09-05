import * as Application from 'expo-application';
import { Platform } from 'react-native';
import { API_BASE_URL, appConfig } from './config';

export type AppRelease = {
  id: number | string;
  versionName: string;
  versionCode: number;
  packageName: string;
  downloadUrl: string;
  sha256: string;
  fileSize: number;
  releaseNote: string;
  forceUpdate: boolean;
  publishedAt: string;
};

export type UpdateData = { updateAvailable: boolean; release: AppRelease | null };

export type NativeAppVersion = { versionName: string; versionCode: number };

export function getNativeAppVersion(): NativeAppVersion | null {
  if (Platform.OS !== 'android') return null;
  const versionName = Application.nativeApplicationVersion;
  const rawBuild = Application.nativeBuildVersion;
  const versionCode = rawBuild ? Number.parseInt(rawBuild, 10) : NaN;
  if (!versionName || !Number.isFinite(versionCode) || versionCode <= 0) return null;
  return { versionName, versionCode };
}

export async function checkForAppUpdate(): Promise<UpdateData> {
  const current = getNativeAppVersion();
  if (!current) throw new Error('当前环境不支持检查 Android 更新');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), appConfig.requestTimeoutMs);
  try {
    const url = `${API_BASE_URL}/api/mobile-app/update?platform=android&versionCode=${encodeURIComponent(current.versionCode)}`;
    const response = await fetch(url, { headers: { Accept: 'application/json' }, signal: controller.signal });
    const body = await response.json() as { code?: number; message?: string; msg?: string; data?: UpdateData };
    if (!response.ok || body.code !== 200 || !body.data) {
      throw new Error(body.message || body.msg || `检查更新失败 (${response.status})`);
    }
    return body.data;
  } catch (error) {
    if (typeof error === 'object' && error !== null && 'name' in error && error.name === 'AbortError') {
      throw new Error('检查更新超时，请稍后重试');
    }
    if (error instanceof TypeError) throw new Error('无法连接服务器，请检查网络');
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '';
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
