import { AppState, AppStateStatus, Linking, Modal, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { colors, radii } from '../components/theme';
import { AppRelease, checkForAppUpdate, downloadAndInstallApk, formatFileSize, getNativeAppVersion, UpdateData } from '../lib/updates';

type UpdateContextValue = {
  currentVersion: string;
  checking: boolean;
  checkNow: () => Promise<boolean>;
};

const UpdateContext = createContext<UpdateContextValue | undefined>(undefined);

export function UpdateProvider({ children }: React.PropsWithChildren) {
  const native = useMemo(() => getNativeAppVersion(), []);
  const [release, setRelease] = useState<AppRelease | null>(null);
  const [checking, setChecking] = useState(false);
  const [manualError, setManualError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [progress, setProgress] = useState(0);
  const inflight = useRef<Promise<boolean> | null>(null);
  const lastCheck = useRef(0);

  const checkNow = useCallback(async (manual = true): Promise<boolean> => {
    if (Platform.OS !== 'android' || !native) {
      if (manual) setManualError('当前环境不支持检查 Android 更新');
      return false;
    }
    if (inflight.current) return inflight.current;
    if (!manual && Date.now() - lastCheck.current < 60_000) return false;
    setChecking(true);
    if (manual) setManualError(null);
    const promise = checkForAppUpdate().then((data: UpdateData) => {
      lastCheck.current = Date.now();
      const available = Boolean(data.updateAvailable && data.release);
      if (available && data.release) setRelease(data.release);
      if (manual && !available) setManualError('当前已是最新版本');
      return available;
    }).catch((error: unknown) => {
      if (manual) setManualError(error instanceof Error ? error.message : '检查更新失败');
      return false;
    }).finally(() => {
      setChecking(false);
      inflight.current = null;
    });
    inflight.current = promise;
    return promise;
  }, [native]);

  useEffect(() => { void checkNow(false); }, [checkNow]);
  useEffect(() => {
    const onStateChange = (state: AppStateStatus) => { if (state === 'active') void checkNow(false); };
    const subscription = AppState.addEventListener('change', onStateChange);
    return () => subscription.remove();
  }, [checkNow]);

  const dismiss = () => { if (!release?.forceUpdate && !downloading) setRelease(null); };
  const download = async () => {
    if (!release?.downloadUrl || downloading) return;
    setDownloading(true);
    setProgress(0);
    try {
      await downloadAndInstallApk(release.downloadUrl, setProgress);
      setRelease(null);
    } catch {
      setDownloading(false);
      setProgress(0);
      try { await Linking.openURL(release.downloadUrl); } catch { /* ignore */ }
    }
  };
  const currentVersion = native ? `v${native.versionName} (${native.versionCode})` : '当前环境不支持';
  const value = useMemo(() => ({ currentVersion, checking, checkNow: () => checkNow(true) }), [currentVersion, checking, checkNow]);

  return <UpdateContext.Provider value={value}>
    {children}
    <Modal visible={Boolean(release)} transparent animationType="fade" onRequestClose={dismiss}>
      <View style={styles.backdrop}><View style={styles.card}>
        <Text style={styles.title}>{release?.forceUpdate ? '需要更新 APP' : '发现新版本'}</Text>
        <Text style={styles.version}>{release ? `v${release.versionName}（版本号 ${release.versionCode}）` : ''}</Text>
        {!!release?.releaseNote && <Text style={styles.notes}>{release.releaseNote}</Text>}
        {!!release?.fileSize && <Text style={styles.size}>安装包大小：{formatFileSize(release.fileSize)}</Text>}
        <Text style={styles.hint}>点击后直接在 APP 内下载并调起系统安装器，按系统提示确认安装即可（无需打开浏览器）。</Text>
        <Pressable style={[styles.download, downloading && styles.downloadDisabled]} onPress={download} disabled={downloading}>
          {downloading ? <Text style={styles.downloadText}>正在下载 {Math.round(progress * 100)}%</Text> : <Text style={styles.downloadText}>立即更新</Text>}
        </Pressable>
        {downloading && <View style={styles.progressTrack}><View style={[styles.progressFill, { width: `${Math.max(2, Math.round(progress * 100))}%` }]} /></View>}
        {!release?.forceUpdate && !downloading && <Pressable style={styles.later} onPress={dismiss}><Text style={styles.laterText}>稍后再说</Text></Pressable>}
      </View></View>
    </Modal>
    {!!manualError && <Modal visible transparent animationType="fade" onRequestClose={() => setManualError(null)}>
      <View style={styles.backdrop}><View style={styles.card}><Text style={styles.title}>检查更新</Text><Text style={styles.notes}>{manualError}</Text><Pressable style={styles.download} onPress={() => setManualError(null)}><Text style={styles.downloadText}>知道了</Text></Pressable></View></View>
    </Modal>}
  </UpdateContext.Provider>;
}

export function useUpdates() { const value = useContext(UpdateContext); if (!value) throw new Error('useUpdates must be used inside UpdateProvider'); return value; }

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(15, 27, 45, 0.48)', justifyContent: 'center', padding: 24 },
  card: { backgroundColor: colors.surface, borderRadius: radii.lg, padding: 22, gap: 10 },
  title: { color: colors.ink, fontSize: 20, fontWeight: '800' },
  version: { color: colors.accent, fontSize: 14, fontWeight: '700' },
  notes: { color: colors.inkSoft, fontSize: 14, lineHeight: 21 },
  size: { color: colors.inkFaint, fontSize: 12 },
  hint: { color: colors.inkFaint, fontSize: 11, lineHeight: 17 },
  download: { backgroundColor: colors.brand, minHeight: 48, borderRadius: 14, alignItems: 'center', justifyContent: 'center', marginTop: 4 },
  downloadDisabled: { opacity: 0.7 },
  downloadText: { color: colors.white, fontSize: 14, fontWeight: '800' },
  progressTrack: { height: 8, borderRadius: 4, backgroundColor: '#EEF1F6', overflow: 'hidden' },
  progressFill: { height: 8, borderRadius: 4, backgroundColor: colors.accent },
  later: { minHeight: 42, alignItems: 'center', justifyContent: 'center' },
  laterText: { color: colors.inkFaint, fontSize: 13, fontWeight: '700' },
});
