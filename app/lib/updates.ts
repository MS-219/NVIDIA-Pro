import * as Application from 'expo-application';
import { File, Paths } from 'expo-file-system';
import * as IntentLauncher from 'expo-intent-launcher';
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

/**
 * 在 APP 内下载 APK 并调起系统安装器（不经过浏览器）。
 * 下载到缓存目录，通过 FileProvider 以 content:// URI 暴露给系统 PackageInstaller。
 * @param onProgress 下载进度回调，参数为 0~1 的完成比例。
 */
export async function downloadAndInstallApk(url: string, onProgress?: (ratio: number) => void): Promise<void> {
  if (Platform.OS !== 'android') throw new Error('仅支持在 Android 上安装更新');
  const applicationId = Application.applicationId || 'cn.juxin.orin.app';
  const file = new File(Paths.cache, 'app-update.apk');
  if (file.exists) { try { file.delete(); } catch { /* ignore */ } }
  const task = File.createDownloadTask(url, file, {
    onProgress: ({ bytesWritten, totalBytes }) => {
      if (totalBytes > 0) onProgress?.(Math.min(1, bytesWritten / totalBytes));
    },
  });
  await task.downloadAsync();
  const contentUri = `content://${applicationId}.fileprovider/cache/app-update.apk`;
  await IntentLauncher.startActivityAsync('android.intent.action.VIEW', {
    data: contentUri,
    type: 'application/vnd.android.package-archive',
    flags: 1, // FLAG_GRANT_READ_URI_PERMISSION
  });
}
