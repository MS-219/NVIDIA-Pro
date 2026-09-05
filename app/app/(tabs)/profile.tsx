import { Ionicons } from '@expo/vector-icons';
import { Link } from 'expo-router';
import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { BrandMark } from '../../components/BrandMark';
import { StatusPill } from '../../components/StatusPill';
import { colors, radii } from '../../components/theme';
import { useAuth } from '../../context/AuthContext';
import { useDevices } from '../../context/DeviceContext';
import { useUpdates } from '../../context/UpdateContext';

export default function ProfileScreen() {
  const { session, signOut } = useAuth();
  const { devices } = useDevices();
  const { currentVersion, checking, checkNow } = useUpdates();
  const nickname = session?.user.nickname || 'Orin 用户';

  return (
    <ScrollView style={styles.page} contentContainerStyle={styles.content} contentInsetAdjustmentBehavior="automatic" showsVerticalScrollIndicator={false}>
      <View style={styles.header}><BrandMark compact /><Text style={styles.headerTitle}>我的</Text><View style={styles.headerSpacer} /></View>

      <View style={styles.identity}>
        <View style={styles.identityAvatar}><Ionicons name="person" size={29} color={colors.forest} /></View>
        <View style={styles.identityCopy}><Text style={styles.identityName}>{nickname}</Text><Text style={styles.identityPhone} selectable>{session?.user.phone || '--'}</Text><StatusPill label="短信验证账户" icon="shield-checkmark-outline" /></View>
      </View>

      <View style={styles.quickStats}><Stat label="账户 ID" value={session?.user.userId ? `${session.user.userId}` : '--'} /><View style={styles.statLine} /><Stat label="绑定节点" value={`${devices.length}`} /><View style={styles.statLine} /><Stat label="账户状态" value="正常" /></View>

      <Text style={styles.sectionTitle}>账户设置</Text>
      <View style={styles.menuPanel}>
        <MenuRow icon="person-circle-outline" title="个人资料" detail="昵称与账户信息" href="/profile/edit" />
        <MenuRow icon="shield-checkmark-outline" title="登录与安全" detail="手机号短信验证" href="/security" />
        <MenuRow icon="notifications-outline" title="通知设置" detail="管理平台消息" href="/notifications" last />
      </View>

      <Text style={styles.sectionTitle}>应用更新</Text>
      <View style={styles.menuPanel}>
        <MenuRow icon="cloud-download-outline" title="检查更新" detail={checking ? '正在检查…' : `当前版本 ${currentVersion}`} onPress={() => { void checkNow(); }} last />
      </View>

      <Text style={styles.sectionTitle}>关于 APP</Text>
      <View style={styles.menuPanel}>
        <MenuRow icon="server-outline" title="服务环境" detail="独立 API 与数据库" />
        <MenuRow icon="document-text-outline" title="服务协议与隐私政策" detail="查看相关条款" last />
      </View>

      <Pressable onPress={signOut} style={({ pressed }) => [styles.logout, pressed && styles.pressed]} accessibilityRole="button" accessibilityLabel="退出登录">
        <Ionicons name="log-out-outline" size={19} color={colors.coral} /><Text style={styles.logoutText}>退出登录</Text>
      </Pressable>
      <Text style={styles.version}>聚芯节点 · {currentVersion}</Text>
    </ScrollView>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return <View style={styles.stat}><Text style={styles.statLabel}>{label}</Text><Text style={styles.statValue} selectable>{value}</Text></View>;
}

function MenuRow({ icon, title, detail, href, onPress, last = false }: { icon: keyof typeof Ionicons.glyphMap; title: string; detail: string; href?: string; onPress?: () => void; last?: boolean }) {
  const row = <View style={[styles.menuRow, last && styles.menuRowLast]}><View style={styles.menuIcon}><Ionicons name={icon} size={18} color={colors.forest} /></View><View style={styles.menuCopy}><Text style={styles.menuTitle}>{title}</Text><Text style={styles.menuDetail}>{detail}</Text></View><Ionicons name="chevron-forward" size={16} color={colors.inkFaint} /></View>;
  if (onPress) return <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={title}>{row}</Pressable>;
  if (!href) return row;
  return <Link href={href as never} asChild><Pressable accessibilityRole="button" accessibilityLabel={title}>{row}</Pressable></Link>;
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: colors.canvas },
  content: { paddingHorizontal: 20, paddingTop: 12, paddingBottom: 44, gap: 16 },
  header: { minHeight: 40, flexDirection: 'row', alignItems: 'center' },
  headerTitle: { color: colors.ink, fontSize: 16, fontWeight: '700', marginLeft: 12 },
  headerSpacer: { flex: 1 },
  identity: { backgroundColor: colors.forest, borderRadius: radii.lg, padding: 20, flexDirection: 'row', alignItems: 'center' },
  identityAvatar: { width: 64, height: 64, borderRadius: 21, backgroundColor: colors.lime, alignItems: 'center', justifyContent: 'center' },
  identityCopy: { flex: 1, marginLeft: 14, gap: 7 },
  identityName: { color: colors.white, fontSize: 19, fontWeight: '700' },
  identityPhone: { color: '#c2d1bd', fontSize: 13, letterSpacing: 0.5 },
  quickStats: { backgroundColor: colors.surface, borderRadius: radii.md, borderWidth: 1, borderColor: colors.line, paddingVertical: 16, paddingHorizontal: 10, flexDirection: 'row', alignItems: 'center' },
  stat: { flex: 1, alignItems: 'center' },
  statLabel: { color: colors.inkFaint, fontSize: 10 },
  statValue: { color: colors.ink, fontSize: 15, fontWeight: '700', marginTop: 6 },
  statLine: { width: 1, height: 28, backgroundColor: colors.line },
  sectionTitle: { color: colors.ink, fontSize: 15, fontWeight: '700', marginTop: 3, marginBottom: -5 },
  menuPanel: { backgroundColor: colors.surface, borderRadius: radii.md, borderWidth: 1, borderColor: colors.line, paddingHorizontal: 14 },
  menuRow: { minHeight: 65, borderBottomWidth: 1, borderBottomColor: '#edf1eb', flexDirection: 'row', alignItems: 'center' },
  menuRowLast: { borderBottomWidth: 0 },
  menuIcon: { width: 36, height: 36, borderRadius: 12, backgroundColor: '#e5efdc', alignItems: 'center', justifyContent: 'center' },
  menuCopy: { flex: 1, marginLeft: 11 },
  menuTitle: { color: colors.ink, fontSize: 13, fontWeight: '700' },
  menuDetail: { color: colors.inkFaint, fontSize: 11, marginTop: 4 },
  logout: { minHeight: 52, borderRadius: 14, borderWidth: 1, borderColor: '#efd8d3', backgroundColor: '#fffaf9', alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 8, marginTop: 2 },
  logoutText: { color: colors.coral, fontSize: 14, fontWeight: '700' },
  pressed: { opacity: 0.75, transform: [{ scale: 0.985 }] },
  version: { color: colors.inkFaint, fontSize: 10, textAlign: 'center', marginTop: -4 },
});
