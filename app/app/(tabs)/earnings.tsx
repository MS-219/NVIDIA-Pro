import { Ionicons } from '@expo/vector-icons';
import { Link } from 'expo-router';
import React from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SectionHeader } from '../../components/SectionHeader';
import { StatusPill } from '../../components/StatusPill';
import { colors, radii, shadow } from '../../components/theme';
import { useDevices } from '../../context/DeviceContext';

export default function EarningsScreen() {
  const { devices, earnings, loading, error, refresh } = useDevices();
  const hasRecords = earnings.items.length > 0;
  const today = Number(earnings.todayEarnings || 0);
  const total = Number(earnings.totalEarnings || 0);

  return (
    <ScrollView
      style={styles.page}
      contentContainerStyle={styles.content}
      contentInsetAdjustmentBehavior="automatic"
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={loading} onRefresh={refresh} tintColor={colors.leaf} />}
    >
      <View style={styles.header}>
        <View><Text style={styles.kicker}>SETTLEMENT CENTER</Text><Text style={styles.title}>收益</Text></View>
        <StatusPill label={loading ? '同步中' : error ? '同步异常' : '实时同步'} tone={error ? 'danger' : loading ? 'pending' : 'muted'} icon={error ? 'cloud-offline-outline' : 'sync-outline'} />
      </View>

      <View style={styles.balanceHero}>
        <View style={styles.balanceTop}><View><Text style={styles.balanceLabel}>累计结算收益</Text><Text style={styles.balanceValue} selectable>{formatCurrency(total)}</Text></View><View style={styles.balanceIcon}><Ionicons name="wallet-outline" size={22} color={colors.lime} /></View></View>
        <Text style={styles.balanceHint}>{hasRecords ? '来自新 APP 节点账本' : devices.length ? '节点首次结算后更新' : '绑定并上线节点后开始累计'}</Text>
        <View style={styles.balanceDivider} />
        <View style={styles.balanceMeta}><View><Text style={styles.metaLabel}>今日收益</Text><Text style={styles.metaValue} selectable>{formatCurrency(today)}</Text></View><View><Text style={styles.metaLabel}>结算节点</Text><Text style={styles.metaValue}>{earnings.items.length || devices.length}</Text></View><View><Text style={styles.metaLabel}>账本状态</Text><Text style={styles.metaValue}>{hasRecords ? '已同步' : '待开始'}</Text></View></View>
      </View>

      {!!error && <View style={styles.errorBanner}><Ionicons name="cloud-offline-outline" size={17} color={colors.coral} /><Text style={styles.errorText}>暂时无法同步收益数据，下拉页面重试。</Text></View>}

      <SectionHeader title="收益明细" subtitle={hasRecords ? '按节点查看最新结算快照' : '所有数据来自新 APP 后端'} />
      {hasRecords ? (
        <View style={styles.recordList}>
          {earnings.items.map((item) => <EarningRow key={item.deviceId} name={item.deviceName} today={item.todayEarnings} total={item.totalEarnings} updatedAt={item.updatedAt} />)}
        </View>
      ) : (
        <View style={styles.empty}>
          <View style={styles.emptyIcon}><Ionicons name="bar-chart-outline" size={28} color={colors.forest} /></View>
          <Text style={styles.emptyTitle}>还没有收益记录</Text>
          <Text style={styles.emptyText}>设备正常运行并完成首笔结算后，明细会自动显示在这里。</Text>
          <Link href="/(tabs)/devices" asChild><Pressable style={styles.linkButton} accessibilityRole="button" accessibilityLabel="查看设备"><Text style={styles.linkText}>查看设备</Text><Ionicons name="arrow-forward" size={15} color={colors.forest} /></Pressable></Link>
        </View>
      )}

      <View style={styles.note}><Ionicons name="lock-closed-outline" size={16} color={colors.leaf} /><Text style={styles.noteText}>这是全新的收益账本，与旧小程序余额和记录相互独立。</Text></View>
    </ScrollView>
  );
}

function EarningRow({ name, today, total, updatedAt }: { name: string; today: number; total: number; updatedAt: string | null }) {
  return (
    <View style={styles.recordRow}>
      <View style={styles.recordIcon}><Ionicons name="hardware-chip-outline" size={18} color={colors.forest} /></View>
      <View style={styles.recordCopy}><Text style={styles.recordName}>{name}</Text><Text style={styles.recordDate}>{updatedAt ? `更新于 ${formatDate(updatedAt)}` : '等待节点上报'}</Text></View>
      <View style={styles.recordAmount}><Text style={styles.recordToday} selectable>{formatCurrency(today)}</Text><Text style={styles.recordTotal}>累计 {formatCurrency(total)}</Text></View>
    </View>
  );
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
  content: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 42, gap: 17 },
  header: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  kicker: { color: colors.leaf, fontSize: 10, fontWeight: '700', letterSpacing: 1.8, marginBottom: 6 },
  title: { color: colors.ink, fontSize: 29, fontWeight: '700' },
  balanceHero: { backgroundColor: colors.forest, borderRadius: radii.lg, padding: 20, ...shadow.soft },
  balanceTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  balanceLabel: { color: '#bad0b4', fontSize: 12 },
  balanceValue: { color: colors.white, fontSize: 35, fontWeight: '700', marginTop: 8, fontVariant: ['tabular-nums'] },
  balanceIcon: { width: 44, height: 44, borderRadius: 14, backgroundColor: 'rgba(214,237,155,0.16)', alignItems: 'center', justifyContent: 'center' },
  balanceHint: { color: '#b4c8ae', fontSize: 11, marginTop: 6 },
  balanceDivider: { height: 1, backgroundColor: 'rgba(255,255,255,0.14)', marginTop: 21, marginBottom: 16 },
  balanceMeta: { flexDirection: 'row', justifyContent: 'space-between' },
  metaLabel: { color: '#9fb89a', fontSize: 10 },
  metaValue: { color: colors.white, fontSize: 13, fontWeight: '700', marginTop: 5 },
  errorBanner: { backgroundColor: '#fff7f5', borderColor: '#efd8d3', borderWidth: 1, borderRadius: 12, padding: 11, flexDirection: 'row', alignItems: 'center', gap: 7 },
  errorText: { color: colors.coral, fontSize: 11, flex: 1 },
  recordList: { backgroundColor: colors.surface, borderRadius: radii.md, borderWidth: 1, borderColor: colors.line, paddingHorizontal: 14 },
  recordRow: { minHeight: 72, borderBottomWidth: 1, borderBottomColor: '#edf1eb', flexDirection: 'row', alignItems: 'center' },
  recordRowLast: { borderBottomWidth: 0 },
  recordIcon: { width: 38, height: 38, borderRadius: 12, backgroundColor: '#e5efdc', alignItems: 'center', justifyContent: 'center' },
  recordCopy: { flex: 1, marginLeft: 10 },
  recordName: { color: colors.ink, fontSize: 13, fontWeight: '700' },
  recordDate: { color: colors.inkFaint, fontSize: 10, marginTop: 4 },
  recordAmount: { alignItems: 'flex-end' },
  recordToday: { color: colors.forest, fontSize: 13, fontWeight: '700', fontVariant: ['tabular-nums'] },
  recordTotal: { color: colors.inkFaint, fontSize: 10, marginTop: 4, fontVariant: ['tabular-nums'] },
  empty: { alignItems: 'center', backgroundColor: colors.surface, borderRadius: radii.md, borderWidth: 1, borderColor: colors.line, paddingHorizontal: 25, paddingVertical: 31 },
  emptyIcon: { width: 58, height: 58, borderRadius: 19, backgroundColor: '#e5efdc', alignItems: 'center', justifyContent: 'center', marginBottom: 15 },
  emptyTitle: { color: colors.ink, fontSize: 17, fontWeight: '700' },
  emptyText: { color: colors.inkSoft, fontSize: 12, lineHeight: 19, textAlign: 'center', marginTop: 8, maxWidth: 280 },
  linkButton: { minHeight: 38, flexDirection: 'row', alignItems: 'center', gap: 7, marginTop: 17, paddingHorizontal: 12 },
  linkText: { color: colors.forest, fontSize: 13, fontWeight: '700' },
  note: { flexDirection: 'row', alignItems: 'flex-start', gap: 8, paddingHorizontal: 3 },
  noteText: { color: colors.inkFaint, fontSize: 11, lineHeight: 17, flex: 1 },
});
