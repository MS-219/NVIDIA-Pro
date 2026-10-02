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
  const hasDaily = earnings.dailyEarnings?.length > 0;
  const today = Number(earnings.todayEarnings || 0);
  const total = Number(earnings.walletBalance ?? earnings.totalEarnings ?? 0);

  return (
    <ScrollView
      style={styles.page}
      contentContainerStyle={styles.content}
      contentInsetAdjustmentBehavior="automatic"
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={loading} onRefresh={refresh} tintColor={colors.accent} />}
    >
      <View style={styles.header}>
        <View><Text style={styles.kicker}>SETTLEMENT CENTER</Text><Text style={styles.title}>收益</Text></View>
        <StatusPill label={loading ? '同步中' : error ? '同步异常' : '实时同步'} tone={error ? 'danger' : loading ? 'pending' : 'muted'} icon={error ? 'cloud-offline-outline' : 'sync-outline'} />
      </View>

      <View style={styles.balanceHero}>
        <View style={styles.balanceTop}><View><Text style={styles.balanceLabel}>钱包可用余额</Text><Text style={styles.balanceValue} selectable>{formatCurrency(total)}</Text></View><View style={styles.balanceIcon}><Ionicons name="wallet-outline" size={22} color={colors.highlight} /></View></View>
        <Text style={styles.balanceHint}>{hasRecords ? '已与提现钱包同步' : devices.length ? '节点首次结算后更新' : '绑定并上线节点后开始累计'}</Text>
        <View style={styles.balanceDivider} />
        <View style={styles.balanceMeta}><View><Text style={styles.metaLabel}>今日收益</Text><Text style={styles.metaValue} selectable>{formatCurrency(today)}</Text></View><View><Text style={styles.metaLabel}>结算节点</Text><Text style={styles.metaValue}>{earnings.items.length || devices.length}</Text></View><View><Text style={styles.metaLabel}>账本状态</Text><Text style={styles.metaValue}>{hasRecords ? '已同步' : '待开始'}</Text></View></View>
        <Link href="/wallet" asChild><Pressable style={styles.withdrawButton} accessibilityRole="button" accessibilityLabel="去提现"><Ionicons name="wallet-outline" size={16} color={colors.brand} /><Text style={styles.withdrawText}>去提现</Text><Ionicons name="arrow-forward" size={15} color={colors.brand} /></Pressable></Link>
      </View>

      {!!error && <View style={styles.errorBanner}><Ionicons name="cloud-offline-outline" size={17} color={colors.danger} /><Text style={styles.errorText}>暂时无法同步收益数据，下拉页面重试。</Text></View>}

      <SectionHeader title="收益明细" subtitle={hasRecords ? '按节点查看最新结算快照' : '所有数据来自新 APP 后端'} />
      {hasRecords ? (
        <View style={styles.recordList}>
          {earnings.items.map((item) => <EarningRow key={item.deviceId} code={item.deviceCode} today={item.todayEarnings} total={item.totalEarnings} updatedAt={item.updatedAt} />)}
        </View>
      ) : (
        <View style={styles.empty}>
          <View style={styles.emptyIcon}><Ionicons name="bar-chart-outline" size={28} color={colors.brand} /></View>
          <Text style={styles.emptyTitle}>还没有收益记录</Text>
          <Text style={styles.emptyText}>设备正常运行并完成首笔结算后，明细会自动显示在这里。</Text>
          <Link href="/(tabs)/devices" asChild><Pressable style={styles.linkButton} accessibilityRole="button" accessibilityLabel="查看设备"><Text style={styles.linkText}>查看设备</Text><Ionicons name="arrow-forward" size={15} color={colors.brand} /></Pressable></Link>
        </View>
      )}

      {hasDaily && (
        <>
          <SectionHeader title="每日收益" subtitle="按结算日期汇总" />
          <View style={styles.dailyList}>
            {earnings.dailyEarnings.map((item) => <DailyRow key={item.date} date={item.date} amount={item.amount} />)}
          </View>
        </>
      )}

      <View style={styles.note}><Ionicons name="lock-closed-outline" size={16} color={colors.accent} /><Text style={styles.noteText}>这是全新的收益账本，与旧小程序余额和记录相互独立。</Text></View>
    </ScrollView>
  );
}

function EarningRow({ code, today, total, updatedAt }: { code: string; today: number; total: number; updatedAt: string | null }) {
  return (
    <View style={styles.recordRow}>
      <View style={styles.recordIcon}><Ionicons name="hardware-chip-outline" size={18} color={colors.brand} /></View>
      <View style={styles.recordCopy}><Text style={styles.recordName}>设备 {code}</Text><Text style={styles.recordDate}>{updatedAt ? `更新于 ${formatDate(updatedAt)}` : '等待节点上报'}</Text></View>
      <View style={styles.recordAmount}><Text style={styles.recordToday} selectable>{formatCurrency(today)}</Text><Text style={styles.recordTotal}>累计 {formatCurrency(total)}</Text></View>
    </View>
  );
}

function DailyRow({ date, amount }: { date: string; amount: number }) {
  return (
    <View style={styles.dailyRow}>
      <Text style={styles.dailyDate}>{date}</Text>
      <Text style={styles.dailyAmount} selectable>{formatCurrency(amount)}</Text>
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
  kicker: { color: colors.accent, fontSize: 10, fontWeight: '700', letterSpacing: 1.8, marginBottom: 6 },
  title: { color: colors.ink, fontSize: 29, fontWeight: '700' },
  balanceHero: { backgroundColor: colors.brand, borderRadius: radii.lg, padding: 20, ...shadow.soft },
  balanceTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  balanceLabel: { color: '#A9BBD4', fontSize: 12 },
  balanceValue: { color: colors.white, fontSize: 35, fontWeight: '700', marginTop: 8, fontVariant: ['tabular-nums'] },
  balanceIcon: { width: 44, height: 44, borderRadius: 14, backgroundColor: 'rgba(255,255,255,0.14)', alignItems: 'center', justifyContent: 'center' },
  balanceHint: { color: '#A9BBD4', fontSize: 11, marginTop: 6 },
  balanceDivider: { height: 1, backgroundColor: 'rgba(255,255,255,0.14)', marginTop: 21, marginBottom: 16 },
  balanceMeta: { flexDirection: 'row', justifyContent: 'space-between' },
  metaLabel: { color: '#8593A6', fontSize: 10 },
  metaValue: { color: colors.white, fontSize: 13, fontWeight: '700', marginTop: 5 },
  withdrawButton: { minHeight: 46, borderRadius: 13, backgroundColor: colors.highlight, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 8, marginTop: 18 },
  withdrawText: { color: colors.brand, fontSize: 14, fontWeight: '700' },
  errorBanner: { backgroundColor: '#FDF1F0', borderColor: '#F4D3D0', borderWidth: 1, borderRadius: 12, padding: 11, flexDirection: 'row', alignItems: 'center', gap: 7 },
  errorText: { color: colors.danger, fontSize: 11, flex: 1 },
  recordList: { backgroundColor: colors.surface, borderRadius: radii.md, ...shadow.card, paddingHorizontal: 14 },
  recordRow: { minHeight: 72, borderBottomWidth: 1, borderBottomColor: '#F0F3F7', flexDirection: 'row', alignItems: 'center' },
  recordRowLast: { borderBottomWidth: 0 },
  recordIcon: { width: 38, height: 38, borderRadius: 12, backgroundColor: '#E8EEF7', alignItems: 'center', justifyContent: 'center' },
  recordCopy: { flex: 1, marginLeft: 10 },
  recordName: { color: colors.ink, fontSize: 13, fontWeight: '700' },
  recordDate: { color: colors.inkFaint, fontSize: 10, marginTop: 4 },
  recordAmount: { alignItems: 'flex-end' },
  recordToday: { color: colors.brand, fontSize: 13, fontWeight: '700', fontVariant: ['tabular-nums'] },
  recordTotal: { color: colors.inkFaint, fontSize: 10, marginTop: 4, fontVariant: ['tabular-nums'] },
  dailyList: { backgroundColor: colors.surface, borderRadius: radii.md, ...shadow.card, paddingHorizontal: 14 },
  dailyRow: { minHeight: 54, borderBottomWidth: 1, borderBottomColor: '#F0F3F7', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  dailyDate: { color: colors.ink, fontSize: 13, fontWeight: '700' },
  dailyAmount: { color: colors.brand, fontSize: 14, fontWeight: '700', fontVariant: ['tabular-nums'] },
  empty: { alignItems: 'center', backgroundColor: colors.surface, borderRadius: radii.md, ...shadow.card, paddingHorizontal: 25, paddingVertical: 31 },
  emptyIcon: { width: 58, height: 58, borderRadius: 19, backgroundColor: '#E8EEF7', alignItems: 'center', justifyContent: 'center', marginBottom: 15 },
  emptyTitle: { color: colors.ink, fontSize: 17, fontWeight: '700' },
  emptyText: { color: colors.inkSoft, fontSize: 12, lineHeight: 19, textAlign: 'center', marginTop: 8, maxWidth: 280 },
  linkButton: { minHeight: 38, flexDirection: 'row', alignItems: 'center', gap: 7, marginTop: 17, paddingHorizontal: 12 },
  linkText: { color: colors.brand, fontSize: 13, fontWeight: '700' },
  note: { flexDirection: 'row', alignItems: 'flex-start', gap: 8, paddingHorizontal: 3 },
  noteText: { color: colors.inkFaint, fontSize: 11, lineHeight: 17, flex: 1 },
});
