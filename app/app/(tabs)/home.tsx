import { Ionicons } from '@expo/vector-icons';
import { Link } from 'expo-router';
import React from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { BrandMark } from '../../components/BrandMark';
import { SectionHeader } from '../../components/SectionHeader';
import { StatusPill } from '../../components/StatusPill';
import { colors, radii, shadow } from '../../components/theme';
import { useAuth } from '../../context/AuthContext';
import { useDevices } from '../../context/DeviceContext';

const chartBars = [0.28, 0.42, 0.34, 0.64, 0.48, 0.74, 0.54];

export default function HomeScreen() {
  const { session } = useAuth();
  const { devices, summary, earnings, loading, error, refresh } = useDevices();
  const nickname = session?.user.nickname || 'Orin 用户';
  const activeDevices = summary?.online ?? devices.filter((device) => device.status === 'online').length;
  const todayEarnings = summary?.todayEarnings ?? earnings.todayEarnings;
  const totalEarnings = summary?.totalEarnings ?? earnings.totalEarnings;
  const hasDevices = devices.length > 0;

  return (
    <ScrollView
      style={styles.page}
      contentContainerStyle={styles.content}
      contentInsetAdjustmentBehavior="automatic"
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={loading} onRefresh={refresh} tintColor={colors.leaf} />}
    >
      <View style={styles.topBar}>
        <BrandMark compact />
        <Link href="/notifications" asChild>
          <Pressable style={styles.iconButton} accessibilityRole="button" accessibilityLabel="查看通知" hitSlop={8}>
            <Ionicons name="notifications-outline" size={20} color={colors.ink} />
            <View style={styles.notificationDot} />
          </Pressable>
        </Link>
      </View>

      <View style={styles.hero}>
        <View style={styles.heroTop}>
          <View style={styles.heroCopy}>
            <Text style={styles.heroKicker}>ORIN CONTROL CENTER</Text>
            <Text style={styles.heroTitle}>你好，{nickname}</Text>
            <Text style={styles.heroSubtitle}>你的节点网络，随时可查看。</Text>
          </View>
          <StatusPill label="账户已验证" icon="shield-checkmark-outline" />
        </View>

        <View style={styles.heroDivider} />
        <View style={styles.heroStats}>
          <View style={styles.heroStat}>
            <Text style={styles.heroStatLabel}>在线节点</Text>
            <Text style={styles.heroStatValue} selectable>{summary ? activeDevices : '--'}</Text>
          </View>
          <View style={styles.heroStatLine} />
          <View style={styles.heroStat}>
            <Text style={styles.heroStatLabel}>今日收益</Text>
            <Text style={styles.heroStatValue} selectable>{summary !== null || earnings.todayEarnings > 0 ? formatCurrency(todayEarnings) : '--'}</Text>
          </View>
        </View>
        <View style={styles.heroFooter}>
          <Ionicons name="lock-closed-outline" size={13} color={colors.lime} />
          <Text style={styles.heroFooterText}>独立 APP 账户 · 数据仅同步自新后端</Text>
        </View>
      </View>

      {!!error && <View style={styles.syncBanner}><Ionicons name="cloud-offline-outline" size={17} color={colors.coral} /><Text style={styles.syncText}>数据同步暂时中断，向下拉可重试</Text></View>}

      <SectionHeader title="运行概览" subtitle="节点数据会在上线后自动更新" href="/(tabs)/earnings" actionLabel="收益明细" />
      <View style={styles.metricGrid}>
        <MetricCard icon="wallet-outline" label="累计收益" value={summary !== null || earnings.totalEarnings > 0 ? formatCurrency(totalEarnings) : '--'} hint={totalEarnings > 0 ? '新账本累计结算' : '等待结算数据'} tone="lime" />
        <MetricCard icon="hardware-chip-outline" label="绑定节点" value={summary ? `${summary.total}` : hasDevices ? `${devices.length}` : '--'} hint={hasDevices ? `${activeDevices} 个在线运行` : '还未添加节点'} tone="amber" />
      </View>

      <View style={styles.chartPanel}>
        <View style={styles.chartHeader}>
          <View>
            <Text style={styles.chartTitle}>收益趋势</Text>
            <Text style={styles.chartSubtitle}>近 7 日 · {earnings.items.length ? '按节点汇总' : '等待首笔结算'}</Text>
          </View>
          <View style={styles.chartLegend}><View style={styles.legendMark} /><Text style={styles.legendText}>{earnings.items.length ? '已同步' : '待同步'}</Text></View>
        </View>
        <View style={styles.chartArea}>
          <View style={styles.chartGridLine} />
          <View style={[styles.chartGridLine, styles.chartGridLineMiddle]} />
          <View style={[styles.chartGridLine, styles.chartGridLineBottom]} />
          <View style={styles.bars}>
            {chartBars.map((height, index) => (
              <View key={index} style={styles.barColumn}>
                <View style={[styles.bar, { height: `${height * 100}%` }]} />
                <Text style={styles.barLabel}>{['一', '二', '三', '四', '五', '六', '日'][index]}</Text>
              </View>
            ))}
          </View>
          <View style={styles.chartEmptyOverlay}><Text style={styles.chartEmptyText}>{earnings.items.length ? '历史趋势接口接入后展示' : '设备上线后生成真实趋势'}</Text></View>
        </View>
      </View>

      <SectionHeader title="快捷入口" subtitle="常用操作" />
      <View style={styles.actionGrid}>
        <QuickAction href="/add-device" icon="add-circle-outline" title="绑定节点" subtitle="输入绑定码" />
        <QuickAction href="/(tabs)/devices" icon="hardware-chip-outline" title="设备管理" subtitle="查看在线状态" />
        <QuickAction href="/(tabs)/earnings" icon="bar-chart-outline" title="收益明细" subtitle="查看结算记录" />
        <QuickAction href="/notifications" icon="megaphone-outline" title="平台通知" subtitle="查看最新消息" />
      </View>

      <View style={styles.activityHeader}><Text style={styles.activityTitle}>系统状态</Text><StatusPill label="服务正常" icon="checkmark-circle-outline" /></View>
      <View style={styles.activityPanel}>
        <ActivityRow icon="cloud-done-outline" title="新 APP API" detail="独立服务已就绪" />
        <ActivityRow icon="chatbubble-ellipses-outline" title="短信登录" detail="阿里云短信通道" />
        <ActivityRow icon="sync-outline" title="设备同步" detail={hasDevices ? '等待节点首次上报' : '绑定节点后开始'} last />
      </View>
    </ScrollView>
  );
}

function MetricCard({ icon, label, value, hint, tone }: { icon: keyof typeof Ionicons.glyphMap; label: string; value: string; hint: string; tone: 'lime' | 'amber' }) {
  return (
    <View style={styles.metricCard}>
      <View style={[styles.metricIcon, tone === 'amber' && styles.metricIconAmber]}><Ionicons name={icon} size={18} color={tone === 'amber' ? colors.amber : colors.forest} /></View>
      <Text style={styles.metricLabel}>{label}</Text>
      <Text style={styles.metricValue} selectable>{value}</Text>
      <Text style={styles.metricHint}>{hint}</Text>
    </View>
  );
}

function QuickAction({ href, icon, title, subtitle }: { href: string; icon: keyof typeof Ionicons.glyphMap; title: string; subtitle: string }) {
  return (
    <Link href={href as never} asChild>
      <Pressable style={styles.actionCard} accessibilityRole="button" accessibilityLabel={`${title}，${subtitle}`}>
        <View style={styles.actionIcon}><Ionicons name={icon} size={20} color={colors.forest} /></View>
        <View style={styles.actionCopy}><Text style={styles.actionTitle}>{title}</Text><Text style={styles.actionSubtitle}>{subtitle}</Text></View>
        <Ionicons name="chevron-forward" size={16} color={colors.inkFaint} />
      </Pressable>
    </Link>
  );
}

function ActivityRow({ icon, title, detail, last = false }: { icon: keyof typeof Ionicons.glyphMap; title: string; detail: string; last?: boolean }) {
  return (
    <View style={[styles.activityRow, last && styles.activityRowLast]}>
      <View style={styles.activityIcon}><Ionicons name={icon} size={17} color={colors.leaf} /></View>
      <View style={styles.activityCopy}><Text style={styles.activityRowTitle}>{title}</Text><Text style={styles.activityDetail}>{detail}</Text></View>
      <Ionicons name="checkmark" size={16} color={colors.leaf} />
    </View>
  );
}

function formatCurrency(value: number): string {
  return `¥${Number(value || 0).toFixed(2)}`;
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: colors.canvas },
  content: { paddingHorizontal: 20, paddingTop: 10, paddingBottom: 42, gap: 16 },
  topBar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', minHeight: 40 },
  iconButton: { width: 40, height: 40, borderRadius: 13, backgroundColor: colors.surface, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line },
  notificationDot: { position: 'absolute', top: 9, right: 10, width: 6, height: 6, borderRadius: 3, backgroundColor: colors.coral, borderWidth: 1, borderColor: colors.surface },
  hero: { backgroundColor: colors.forest, borderRadius: radii.lg, padding: 20, marginTop: 4, ...shadow.soft },
  heroTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 },
  heroCopy: { flex: 1 },
  heroKicker: { color: '#b6c9ae', fontSize: 10, fontWeight: '700', letterSpacing: 1.8, marginBottom: 9 },
  heroTitle: { color: colors.white, fontSize: 25, fontWeight: '700' },
  heroSubtitle: { color: '#c1d1bc', fontSize: 12, marginTop: 7 },
  heroDivider: { height: 1, backgroundColor: 'rgba(255,255,255,0.14)', marginTop: 21, marginBottom: 17 },
  heroStats: { flexDirection: 'row', alignItems: 'center' },
  heroStat: { flex: 1 },
  heroStatLine: { width: 1, height: 35, backgroundColor: 'rgba(255,255,255,0.15)', marginHorizontal: 10 },
  heroStatLabel: { color: '#abc1a4', fontSize: 11 },
  heroStatValue: { color: colors.white, fontSize: 19, fontWeight: '700', marginTop: 6, fontVariant: ['tabular-nums'] },
  heroFooter: { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 20 },
  heroFooterText: { color: '#b7c9b1', fontSize: 10 },
  syncBanner: { backgroundColor: '#fff7f5', borderColor: '#efd8d3', borderWidth: 1, borderRadius: 12, paddingHorizontal: 12, paddingVertical: 10, flexDirection: 'row', alignItems: 'center', gap: 7 },
  syncText: { color: colors.coral, fontSize: 11, flex: 1 },
  metricGrid: { flexDirection: 'row', gap: 12 },
  metricCard: { flex: 1, backgroundColor: colors.surface, borderRadius: radii.md, padding: 15, borderWidth: 1, borderColor: colors.line },
  metricIcon: { width: 34, height: 34, borderRadius: 11, backgroundColor: '#e4f0d8', alignItems: 'center', justifyContent: 'center', marginBottom: 12 },
  metricIconAmber: { backgroundColor: '#f5ead8' },
  metricLabel: { color: colors.inkSoft, fontSize: 12 },
  metricValue: { color: colors.ink, fontSize: 25, fontWeight: '700', marginTop: 8, fontVariant: ['tabular-nums'] },
  metricHint: { color: colors.inkFaint, fontSize: 10, marginTop: 5 },
  chartPanel: { backgroundColor: colors.surface, borderRadius: radii.md, padding: 16, borderWidth: 1, borderColor: colors.line },
  chartHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  chartTitle: { color: colors.ink, fontSize: 15, fontWeight: '700' },
  chartSubtitle: { color: colors.inkFaint, fontSize: 11, marginTop: 4 },
  chartLegend: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  legendMark: { width: 7, height: 7, borderRadius: 4, backgroundColor: colors.lime },
  legendText: { color: colors.inkFaint, fontSize: 10 },
  chartArea: { height: 144, marginTop: 17, position: 'relative', overflow: 'hidden' },
  chartGridLine: { position: 'absolute', left: 0, right: 0, top: 8, height: 1, backgroundColor: '#edf1eb' },
  chartGridLineMiddle: { top: '50%' },
  chartGridLineBottom: { top: '88%' },
  bars: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-around', height: 116, paddingHorizontal: 4 },
  barColumn: { height: 128, flex: 1, alignItems: 'center', justifyContent: 'flex-end', gap: 7 },
  bar: { width: 16, maxHeight: 90, minHeight: 20, borderRadius: 8, backgroundColor: '#cfe1b7', opacity: 0.72 },
  barLabel: { color: colors.inkFaint, fontSize: 10 },
  chartEmptyOverlay: { position: 'absolute', left: 20, right: 20, top: 45, alignItems: 'center' },
  chartEmptyText: { color: '#829080', fontSize: 11, backgroundColor: 'rgba(255,255,255,0.86)', paddingHorizontal: 10, paddingVertical: 5, borderRadius: 10 },
  actionGrid: { gap: 10 },
  actionCard: { minHeight: 66, backgroundColor: colors.surface, borderRadius: radii.md, paddingHorizontal: 13, flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: colors.line },
  actionIcon: { width: 37, height: 37, borderRadius: 12, backgroundColor: '#e5efdc', alignItems: 'center', justifyContent: 'center' },
  actionCopy: { flex: 1, marginLeft: 11 },
  actionTitle: { color: colors.ink, fontSize: 13, fontWeight: '700' },
  actionSubtitle: { color: colors.inkFaint, fontSize: 11, marginTop: 3 },
  pressed: { opacity: 0.78, transform: [{ scale: 0.985 }] },
  activityHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 1 },
  activityTitle: { color: colors.ink, fontSize: 16, fontWeight: '700' },
  activityPanel: { backgroundColor: colors.surface, borderRadius: radii.md, paddingHorizontal: 14, borderWidth: 1, borderColor: colors.line },
  activityRow: { minHeight: 63, flexDirection: 'row', alignItems: 'center', borderBottomWidth: 1, borderBottomColor: '#edf1eb' },
  activityRowLast: { borderBottomWidth: 0 },
  activityIcon: { width: 34, height: 34, borderRadius: 11, backgroundColor: '#eef5e9', alignItems: 'center', justifyContent: 'center' },
  activityCopy: { flex: 1, marginLeft: 11 },
  activityRowTitle: { color: colors.ink, fontSize: 12, fontWeight: '700' },
  activityDetail: { color: colors.inkFaint, fontSize: 11, marginTop: 3 },
});
