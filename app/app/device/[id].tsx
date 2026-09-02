import { Ionicons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import React from 'react';
import { Alert, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { StatusPill } from '../../components/StatusPill';
import { colors, radii, shadow } from '../../components/theme';
import { useDevices } from '../../context/DeviceContext';
import type { AppDevice } from '../../context/DeviceContext';

export default function DeviceDetailScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { devices, loading, mutating, refresh, removeDevice } = useDevices();
  const device = devices.find((item) => String(item.id) === String(id));

  const handleRemove = () => {
    if (!device || mutating) return;
    Alert.alert('解绑节点', `确定要解绑“${device.name}”吗？解绑后它会回到待绑定状态。`, [
      { text: '取消', style: 'cancel' },
      { text: '确认解绑', style: 'destructive', onPress: async () => {
        try {
          await removeDevice(device.id);
          router.back();
        } catch {
          // The context exposes the error on the devices screen; keep this
          // detail page open so the user can retry without losing context.
        }
      } },
    ]);
  };

  return (
    <>
      <Stack.Screen options={{ headerShown: false, title: '节点详情' }} />
      <ScrollView
        style={styles.page}
        contentContainerStyle={styles.content}
        contentInsetAdjustmentBehavior="automatic"
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={loading} onRefresh={refresh} tintColor={colors.leaf} />}
      >
        <View style={styles.nav}><Pressable onPress={() => router.back()} style={styles.navButton} accessibilityRole="button" accessibilityLabel="返回"><Ionicons name="chevron-back" size={22} color={colors.ink} /></Pressable><Text style={styles.navTitle}>节点详情</Text><Pressable onPress={handleRemove} style={styles.navButton} accessibilityRole="button" accessibilityLabel="解绑节点" disabled={!device || mutating}><Ionicons name="ellipsis-horizontal" size={21} color={device ? colors.ink : colors.inkFaint} /></Pressable></View>

        {device ? <DeviceDetails device={device} /> : <MissingDevice />}
      </ScrollView>
    </>
  );
}

function DeviceDetails({ device }: { device: AppDevice }) {
  const online = device.status === 'online';
  const statusLabel = online ? '在线运行' : device.status === 'offline' ? '已离线' : '等待上线';
  const statusTone = online ? 'positive' : device.status === 'offline' ? 'danger' : 'pending';
  return (
    <>
      <View style={styles.hero}>
        <View style={styles.heroTop}><View style={styles.heroIcon}><Ionicons name="hardware-chip" size={30} color={colors.lime} /></View><StatusPill label={statusLabel} tone={statusTone} icon={online ? 'pulse-outline' : device.status === 'offline' ? 'close-circle-outline' : 'time-outline'} /></View>
        <Text style={styles.heroName}>{device.name}</Text><Text style={styles.heroCode} selectable>{device.code}</Text>
        <View style={styles.heroRule} /><View style={styles.heroFooter}><Ionicons name="calendar-outline" size={14} color="#b6cbb0" /><Text style={styles.heroFooterText}>{device.boundAt ? `绑定于 ${formatDate(device.boundAt)}` : '绑定时间待同步'}</Text></View>
      </View>

      <Text style={styles.sectionTitle}>实时遥测</Text>
      <View style={styles.telemetryGrid}><Telemetry icon="speedometer-outline" label="节点算力" value={device.hashrate > 0 ? `${formatNumber(device.hashrate)} TOPS` : '--'} /><Telemetry icon="thermometer-outline" label="设备温度" value={device.temperature === null ? '--' : `${formatNumber(device.temperature)}°C`} /><Telemetry icon="wifi-outline" label="连接状态" value={online ? '稳定' : statusLabel} /><Telemetry icon="time-outline" label="最近上报" value={device.lastReportedAt ? formatDate(device.lastReportedAt) : '--'} /></View>

      <Text style={styles.sectionTitle}>收益快照</Text>
      <View style={styles.earningsCard}><View style={styles.earningMain}><Text style={styles.earningLabel}>今日收益</Text><Text style={styles.earningValue} selectable>{formatCurrency(device.dailyEarnings)}</Text></View><View style={styles.earningDivider} /><View style={styles.earningMain}><Text style={styles.earningLabel}>累计收益</Text><Text style={styles.earningValue} selectable>{formatCurrency(device.totalEarnings)}</Text></View></View>

      <View style={styles.notice}><Ionicons name="information-circle-outline" size={18} color={colors.leaf} /><Text style={styles.noticeText}>节点数据由新 APP 后端同步，和旧小程序设备完全独立。</Text></View>
    </>
  );
}

function Telemetry({ icon, label, value }: { icon: keyof typeof Ionicons.glyphMap; label: string; value: string }) {
  return <View style={styles.telemetry}><View style={styles.telemetryIcon}><Ionicons name={icon} size={18} color={colors.forest} /></View><Text style={styles.telemetryLabel}>{label}</Text><Text style={styles.telemetryValue} selectable>{value}</Text></View>;
}

function MissingDevice() {
  return <View style={styles.missing}><Ionicons name="search-outline" size={28} color={colors.forest} /><Text style={styles.missingTitle}>节点不存在</Text><Text style={styles.missingText}>它可能已经解绑，返回设备列表重新同步。</Text></View>;
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value);
}

function formatCurrency(value: number): string {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(0, 16).replace('T', ' ');
  return date.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: colors.canvas },
  content: { paddingHorizontal: 20, paddingBottom: 42, gap: 17 },
  nav: { minHeight: 58, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  navButton: { width: 40, height: 40, borderRadius: 13, backgroundColor: colors.surface, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line },
  navTitle: { color: colors.ink, fontSize: 16, fontWeight: '700' },
  hero: { backgroundColor: colors.forest, borderRadius: radii.lg, padding: 20, ...shadow.soft },
  heroTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  heroIcon: { width: 58, height: 58, borderRadius: 19, backgroundColor: 'rgba(214,237,155,0.16)', alignItems: 'center', justifyContent: 'center' },
  heroName: { color: colors.white, fontSize: 23, fontWeight: '700', marginTop: 19 },
  heroCode: { color: '#b9cdb3', fontSize: 12, letterSpacing: 0.7, marginTop: 6 },
  heroRule: { height: 1, backgroundColor: 'rgba(255,255,255,0.14)', marginVertical: 17 },
  heroFooter: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  heroFooterText: { color: '#b6cbb0', fontSize: 11 },
  sectionTitle: { color: colors.ink, fontSize: 16, fontWeight: '700', marginTop: 1 },
  telemetryGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  telemetry: { width: '48%', minHeight: 112, backgroundColor: colors.surface, borderRadius: radii.md, padding: 14, borderWidth: 1, borderColor: colors.line },
  telemetryIcon: { width: 34, height: 34, borderRadius: 11, backgroundColor: '#e5efdc', alignItems: 'center', justifyContent: 'center', marginBottom: 11 },
  telemetryLabel: { color: colors.inkFaint, fontSize: 10 },
  telemetryValue: { color: colors.ink, fontSize: 14, fontWeight: '700', marginTop: 5, fontVariant: ['tabular-nums'] },
  earningsCard: { backgroundColor: colors.surface, borderRadius: radii.md, borderWidth: 1, borderColor: colors.line, padding: 17, flexDirection: 'row', alignItems: 'center' },
  earningMain: { flex: 1 },
  earningLabel: { color: colors.inkFaint, fontSize: 11 },
  earningValue: { color: colors.forest, fontSize: 21, fontWeight: '700', marginTop: 7, fontVariant: ['tabular-nums'] },
  earningDivider: { width: 1, height: 40, backgroundColor: colors.line, marginHorizontal: 17 },
  notice: { backgroundColor: '#e9f1e4', borderRadius: 14, padding: 13, flexDirection: 'row', alignItems: 'flex-start', gap: 8 },
  noticeText: { color: colors.inkSoft, fontSize: 11, lineHeight: 17, flex: 1 },
  missing: { backgroundColor: colors.surface, borderRadius: radii.md, borderWidth: 1, borderColor: colors.line, minHeight: 220, alignItems: 'center', justifyContent: 'center', padding: 24 },
  missingTitle: { color: colors.ink, fontSize: 17, fontWeight: '700', marginTop: 13 },
  missingText: { color: colors.inkSoft, fontSize: 12, textAlign: 'center', marginTop: 7 },
});
