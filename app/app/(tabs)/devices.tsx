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
      refreshControl={<RefreshControl refreshing={loading} onRefresh={refresh} tintColor={colors.leaf} />}
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
        <View style={styles.summaryIcon}><Ionicons name="hardware-chip-outline" size={23} color={colors.lime} /></View>
        <View style={styles.summaryCopy}><Text style={styles.summaryTitle}>{hasDevices ? `${devices.length} 个节点` : '还没有节点'}</Text><Text style={styles.summarySubtitle}>{hasDevices ? `${onlineCount} 个在线 · 下拉刷新状态` : '绑定你的第一个 RK3588S 节点'}</Text></View>
        <StatusPill label={hasDevices ? (onlineCount ? '运行中' : '待上线') : '未配置'} tone={hasDevices ? (onlineCount ? 'positive' : 'pending') : 'muted'} icon={hasDevices && onlineCount ? 'pulse-outline' : hasDevices ? 'time-outline' : 'ellipse-outline'} />
      </View>

      {!!error && <View style={styles.errorBanner}><Ionicons name="cloud-offline-outline" size={18} color={colors.coral} /><View style={styles.errorCopy}><Text style={styles.errorTitle}>同步暂时中断</Text><Text style={styles.errorText}>{error}</Text></View><Pressable onPress={refresh} style={styles.retryButton} accessibilityRole="button" accessibilityLabel="重新同步"><Ionicons name="refresh" size={16} color={colors.coral} /></Pressable></View>}

      <SectionHeader title="我的节点" subtitle="独立 APP 设备清单" />
      {devices.length ? (
        <View style={styles.list}>
          {devices.map((device) => <DeviceCard key={device.id} device={device} />)}
        </View>
      ) : (
        <View style={styles.emptyPanel}>
          <View style={styles.emptyIllustration}>
            <View style={styles.illustrationCore}><Ionicons name="hardware-chip-outline" size={36} color={colors.forest} /></View>
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

      <View style={styles.tip}><Ionicons name="information-circle-outline" size={18} color={colors.leaf} /><View style={styles.tipCopy}><Text style={styles.tipTitle}>设备安全</Text><Text style={styles.tipText}>绑定码只用于建立新 APP 账户与节点的关系，不会读取旧小程序数据。</Text></View></View>
    </ScrollView>
  );
}

function DeviceCard({ device }: { device: AppDevice }) {
  const { name, status } = device;
  const statusLabel = status === 'online' ? '在线' : status === 'offline' ? '离线' : '待上线';
  const statusIcon = status === 'online' ? 'checkmark-circle-outline' : status === 'offline' ? 'close-circle-outline' : 'time-outline';
  const hashrate = device.hashrate > 0 ? `${formatNumber(device.hashrate)} TOPS` : '--';
  const temperature = device.temperature === null || Number.isNaN(device.temperature) ? '--' : `${formatNumber(device.temperature)}°`;
  return (
    <Link href={`/device/${device.id}` as never} asChild>
      <Pressable style={styles.deviceCard} accessibilityRole="button" accessibilityLabel={`查看${name}详情`}>
        <View style={styles.deviceTop}><View style={styles.deviceIcon}><Ionicons name="hardware-chip" size={21} color={colors.forest} /></View><View style={styles.deviceCopy}><Text style={styles.deviceName}>{name}</Text><Text style={styles.deviceCode}>设备型号：RK3588S</Text></View><StatusPill label={statusLabel} tone={status === 'online' ? 'positive' : status === 'offline' ? 'danger' : 'pending'} icon={statusIcon} /></View>
        <View style={styles.deviceDivider} />
        <View style={styles.deviceMeta}><View><Text style={styles.metaLabel}>算力</Text><Text style={styles.metaValue}>{hashrate}</Text></View><View><Text style={styles.metaLabel}>温度</Text><Text style={styles.metaValue}>{temperature}</Text></View><View><Text style={styles.metaLabel}>今日收益</Text><Text style={styles.metaValue}>{formatCurrency(device.dailyEarnings)}</Text></View><Ionicons name="chevron-forward" size={17} color={colors.inkFaint} /></View>
      </Pressable>
    </Link>
  );
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value);
}

function formatCurrency(value: number): string {
  return `¥${Number(value || 0).toFixed(2)}`;
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: colors.canvas },
  content: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 42, gap: 17 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', minHeight: 48 },
  kicker: { color: colors.leaf, fontSize: 10, fontWeight: '700', letterSpacing: 1.8, marginBottom: 6 },
  title: { color: colors.ink, fontSize: 29, fontWeight: '700' },
  addButton: { minHeight: 40, borderRadius: 13, backgroundColor: colors.forest, paddingHorizontal: 13, flexDirection: 'row', alignItems: 'center', gap: 6 },
  addText: { color: colors.white, fontSize: 12, fontWeight: '700' },
  pressed: { opacity: 0.76, transform: [{ scale: 0.985 }] },
  summaryBand: { backgroundColor: colors.forest, borderRadius: radii.lg, padding: 17, flexDirection: 'row', alignItems: 'center', ...shadow.soft },
  summaryIcon: { width: 44, height: 44, borderRadius: 14, backgroundColor: 'rgba(214,237,155,0.16)', alignItems: 'center', justifyContent: 'center' },
  summaryCopy: { flex: 1, marginLeft: 12 },
  summaryTitle: { color: colors.white, fontSize: 16, fontWeight: '700' },
  summarySubtitle: { color: '#b9cdb3', fontSize: 11, marginTop: 5 },
  errorBanner: { backgroundColor: '#fff7f5', borderColor: '#efd8d3', borderWidth: 1, borderRadius: 14, padding: 12, flexDirection: 'row', alignItems: 'center', gap: 9 },
  errorCopy: { flex: 1 },
  errorTitle: { color: colors.coral, fontSize: 12, fontWeight: '700' },
  errorText: { color: colors.inkSoft, fontSize: 10, lineHeight: 15, marginTop: 2 },
  retryButton: { width: 34, height: 34, borderRadius: 11, backgroundColor: '#fbe9e5', alignItems: 'center', justifyContent: 'center' },
  list: { gap: 11 },
  deviceCard: { backgroundColor: colors.surface, borderRadius: radii.md, padding: 15, borderWidth: 1, borderColor: colors.line },
  deviceTop: { flexDirection: 'row', alignItems: 'center' },
  deviceIcon: { width: 42, height: 42, borderRadius: 13, backgroundColor: '#e5efdc', alignItems: 'center', justifyContent: 'center' },
  deviceCopy: { flex: 1, marginLeft: 11 },
  deviceName: { color: colors.ink, fontSize: 14, fontWeight: '700' },
  deviceCode: { color: colors.inkFaint, fontSize: 11, marginTop: 5, letterSpacing: 0.6 },
  deviceDivider: { height: 1, backgroundColor: '#edf1eb', marginVertical: 14 },
  deviceMeta: { flexDirection: 'row', alignItems: 'center', gap: 22 },
  metaLabel: { color: colors.inkFaint, fontSize: 10 },
  metaValue: { color: colors.inkSoft, fontSize: 12, fontWeight: '700', marginTop: 4 },
  emptyPanel: { backgroundColor: colors.surface, borderRadius: radii.md, paddingHorizontal: 24, paddingVertical: 28, alignItems: 'center', borderWidth: 1, borderColor: colors.line },
  emptyIllustration: { width: 132, height: 88, alignItems: 'center', justifyContent: 'center', marginBottom: 14, position: 'relative' },
  illustrationCore: { width: 72, height: 72, borderRadius: 24, backgroundColor: '#e4efdc', alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: '#cddfc4' },
  signal: { position: 'absolute', borderWidth: 1, borderColor: '#c6d9bb', borderRadius: 40, width: 110, height: 52, top: 18, opacity: 0.65 },
  signalOne: { transform: [{ rotate: '25deg' }] },
  signalTwo: { transform: [{ rotate: '-25deg' }] },
  emptyTitle: { color: colors.ink, fontSize: 17, fontWeight: '700' },
  emptySubtitle: { color: colors.inkSoft, fontSize: 12, lineHeight: 19, textAlign: 'center', marginTop: 8, maxWidth: 285 },
  emptyButton: { minHeight: 46, borderRadius: 13, backgroundColor: colors.forest, paddingHorizontal: 18, flexDirection: 'row', alignItems: 'center', gap: 7, marginTop: 19 },
  emptyButtonText: { color: colors.white, fontSize: 13, fontWeight: '700' },
  tip: { flexDirection: 'row', alignItems: 'flex-start', gap: 9, paddingHorizontal: 3, marginTop: 2 },
  tipCopy: { flex: 1 },
  tipTitle: { color: colors.ink, fontSize: 12, fontWeight: '700' },
  tipText: { color: colors.inkFaint, fontSize: 11, lineHeight: 17, marginTop: 3 },
});
