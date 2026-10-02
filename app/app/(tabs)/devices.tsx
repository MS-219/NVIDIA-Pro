import { Ionicons } from '@expo/vector-icons';
import { Link } from 'expo-router';
import React from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SectionHeader } from '../../components/SectionHeader';
import { StatusPill } from '../../components/StatusPill';
import { colors, radii, shadow } from '../../components/theme';
import { useDevices } from '../../context/DeviceContext';
import type { AppDevice } from '../../context/DeviceContext';

export default function DevicesScreen() {
  const { devices, summary, loading, error, refresh } = useDevices();
  const onlineCount = summary?.online ?? devices.filter((device) => device.status === 'online').length;
  const hasDevices = devices.length > 0;

  return (
    <ScrollView
      style={styles.page}
      contentContainerStyle={styles.content}
      contentInsetAdjustmentBehavior="automatic"
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={loading} onRefresh={refresh} tintColor={colors.accent} />}
    >
      <View style={styles.header}>
        <View><Text style={styles.kicker}>NODE INVENTORY</Text><Text style={styles.title}>设备</Text></View>
        <Link href="/add-device" asChild>
          <Pressable style={styles.addButton} accessibilityRole="button" accessibilityLabel="绑定新节点">
            <Ionicons name="add" size={18} color={colors.white} /><Text style={styles.addText}>绑定节点</Text>
          </Pressable>
        </Link>
      </View>

      <View style={styles.summaryBand}>
        <View style={styles.summaryIcon}><Ionicons name="hardware-chip-outline" size={23} color={colors.highlight} /></View>
        <View style={styles.summaryCopy}><Text style={styles.summaryTitle}>{hasDevices ? `${devices.length} 个节点` : '还没有节点'}</Text><Text style={styles.summarySubtitle}>{hasDevices ? `${onlineCount} 个在线 · 下拉刷新状态` : '绑定你的第一个节点'}</Text></View>
        <StatusPill label={hasDevices ? (onlineCount ? '运行中' : '待上线') : '未配置'} tone={hasDevices ? (onlineCount ? 'positive' : 'pending') : 'muted'} icon={hasDevices && onlineCount ? 'pulse-outline' : hasDevices ? 'time-outline' : 'ellipse-outline'} />
      </View>

      {!!error && <View style={styles.errorBanner}><Ionicons name="cloud-offline-outline" size={18} color={colors.danger} /><View style={styles.errorCopy}><Text style={styles.errorTitle}>同步暂时中断</Text><Text style={styles.errorText}>{error}</Text></View><Pressable onPress={refresh} style={styles.retryButton} accessibilityRole="button" accessibilityLabel="重新同步"><Ionicons name="refresh" size={16} color={colors.danger} /></Pressable></View>}

      <SectionHeader title="我的节点" subtitle="独立 APP 设备清单" />
      {devices.length ? (
        <View style={styles.list}>
          {devices.map((device) => <DeviceCard key={device.id} device={device} />)}
        </View>
      ) : (
        <View style={styles.emptyPanel}>
          <View style={styles.emptyIllustration}>
            <View style={styles.illustrationCore}><Ionicons name="hardware-chip-outline" size={36} color={colors.brand} /></View>
            <View style={[styles.signal, styles.signalOne]} />
            <View style={[styles.signal, styles.signalTwo]} />
          </View>
          <Text style={styles.emptyTitle}>等待第一个节点</Text>
          <Text style={styles.emptySubtitle}>输入设备绑定码后，节点会出现在这里。设备上线后可查看运行状态与收益。</Text>
          <Link href="/add-device" asChild>
            <Pressable style={styles.emptyButton} accessibilityRole="button" accessibilityLabel="开始绑定节点">
              <Ionicons name="add-circle-outline" size={18} color={colors.white} /><Text style={styles.emptyButtonText}>开始绑定</Text>
            </Pressable>
          </Link>
        </View>
      )}

      <View style={styles.tip}><Ionicons name="information-circle-outline" size={18} color={colors.accent} /><View style={styles.tipCopy}><Text style={styles.tipTitle}>设备安全</Text><Text style={styles.tipText}>绑定码只用于建立新 APP 账户与节点的关系，不会读取旧小程序数据。</Text></View></View>
    </ScrollView>
  );
}

function DeviceCard({ device }: { device: AppDevice }) {
  const { code, status } = device;
  const statusLabel = status === 'online' ? '在线' : status === 'offline' ? '离线' : '待上线';
  const statusIcon = status === 'online' ? 'checkmark-circle-outline' : status === 'offline' ? 'close-circle-outline' : 'time-outline';
  const bindTime = device.boundAt ? formatDate(device.boundAt) : '--';
  return (
    <Link href={`/device/${device.id}` as never} asChild>
      <Pressable style={styles.deviceCard} accessibilityRole="button" accessibilityLabel={`查看设备 ${code} 详情`}>
        <View style={styles.deviceTop}><View style={styles.deviceIcon}><Ionicons name="hardware-chip" size={21} color={colors.brand} /></View><View style={styles.deviceCopy}><Text style={styles.deviceName}>设备 {code}</Text></View><StatusPill label={statusLabel} tone={status === 'online' ? 'positive' : status === 'offline' ? 'danger' : 'pending'} icon={statusIcon} /></View>
        <View style={styles.deviceDivider} />
        <View style={styles.deviceMeta}><View><Text style={styles.metaLabel}>绑定时间</Text><Text style={styles.metaValue}>{bindTime}</Text></View><View><Text style={styles.metaLabel}>今日收益</Text><Text style={styles.metaValue}>{formatCurrency(device.dailyEarnings)}</Text></View><Ionicons name="chevron-forward" size={17} color={colors.inkFaint} /></View>
      </Pressable>
    </Link>
  );
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value);
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(0, 16).replace('T', ' ');
  return date.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' });
}

function formatCurrency(value: number): string {
  return `¥${Number(value || 0).toFixed(2)}`;
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: colors.canvas },
  content: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 42, gap: 17 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', minHeight: 48 },
  kicker: { color: colors.accent, fontSize: 10, fontWeight: '700', letterSpacing: 1.8, marginBottom: 6 },
  title: { color: colors.ink, fontSize: 29, fontWeight: '700' },
  addButton: { minHeight: 40, borderRadius: 13, backgroundColor: colors.brand, paddingHorizontal: 13, flexDirection: 'row', alignItems: 'center', gap: 6 },
  addText: { color: colors.white, fontSize: 12, fontWeight: '700' },
  pressed: { opacity: 0.76, transform: [{ scale: 0.985 }] },
  summaryBand: { backgroundColor: colors.brand, borderRadius: radii.lg, padding: 17, flexDirection: 'row', alignItems: 'center', ...shadow.soft },
  summaryIcon: { width: 44, height: 44, borderRadius: 14, backgroundColor: 'rgba(255,255,255,0.14)', alignItems: 'center', justifyContent: 'center' },
  summaryCopy: { flex: 1, marginLeft: 12 },
  summaryTitle: { color: colors.white, fontSize: 16, fontWeight: '700' },
  summarySubtitle: { color: '#A9BBD4', fontSize: 11, marginTop: 5 },
  errorBanner: { backgroundColor: '#FDF1F0', borderColor: '#F4D3D0', borderWidth: 1, borderRadius: 14, padding: 12, flexDirection: 'row', alignItems: 'center', gap: 9 },
  errorCopy: { flex: 1 },
  errorTitle: { color: colors.danger, fontSize: 12, fontWeight: '700' },
  errorText: { color: colors.inkSoft, fontSize: 10, lineHeight: 15, marginTop: 2 },
  retryButton: { width: 34, height: 34, borderRadius: 11, backgroundColor: '#FDF1F0', alignItems: 'center', justifyContent: 'center' },
  list: { gap: 11 },
  deviceCard: { backgroundColor: colors.surface, borderRadius: radii.md, padding: 15, ...shadow.card },
  deviceTop: { flexDirection: 'row', alignItems: 'center' },
  deviceIcon: { width: 42, height: 42, borderRadius: 13, backgroundColor: '#E8EEF7', alignItems: 'center', justifyContent: 'center' },
  deviceCopy: { flex: 1, marginLeft: 11 },
  deviceName: { color: colors.ink, fontSize: 14, fontWeight: '700' },
  deviceDivider: { height: 1, backgroundColor: '#F0F3F7', marginVertical: 14 },
  deviceMeta: { flexDirection: 'row', alignItems: 'center', gap: 22 },
  metaLabel: { color: colors.inkFaint, fontSize: 10 },
  metaValue: { color: colors.inkSoft, fontSize: 12, fontWeight: '700', marginTop: 4 },
  emptyPanel: { backgroundColor: colors.surface, borderRadius: radii.md, paddingHorizontal: 24, paddingVertical: 28, alignItems: 'center', ...shadow.card },
  emptyIllustration: { width: 132, height: 88, alignItems: 'center', justifyContent: 'center', marginBottom: 14, position: 'relative' },
  illustrationCore: { width: 72, height: 72, borderRadius: 24, backgroundColor: '#E8EEF7', alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: '#DDE4EE' },
  signal: { position: 'absolute', borderWidth: 1, borderColor: '#DDE4EE', borderRadius: 40, width: 110, height: 52, top: 18, opacity: 0.65 },
  signalOne: { transform: [{ rotate: '25deg' }] },
  signalTwo: { transform: [{ rotate: '-25deg' }] },
  emptyTitle: { color: colors.ink, fontSize: 17, fontWeight: '700' },
  emptySubtitle: { color: colors.inkSoft, fontSize: 12, lineHeight: 19, textAlign: 'center', marginTop: 8, maxWidth: 285 },
  emptyButton: { minHeight: 46, borderRadius: 13, backgroundColor: colors.brand, paddingHorizontal: 18, flexDirection: 'row', alignItems: 'center', gap: 7, marginTop: 19 },
  emptyButtonText: { color: colors.white, fontSize: 13, fontWeight: '700' },
  tip: { flexDirection: 'row', alignItems: 'flex-start', gap: 9, paddingHorizontal: 3, marginTop: 2 },
  tipCopy: { flex: 1 },
  tipTitle: { color: colors.ink, fontSize: 12, fontWeight: '700' },
  tipText: { color: colors.inkFaint, fontSize: 11, lineHeight: 17, marginTop: 3 },
});
