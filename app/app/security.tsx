import { Ionicons } from '@expo/vector-icons';
import { Stack, useRouter } from 'expo-router';
import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { StatusPill } from '../components/StatusPill';
import { colors, radii } from '../components/theme';
import { useAuth } from '../context/AuthContext';

export default function SecurityScreen() {
  const router = useRouter();
  const { session } = useAuth();
  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <Stack.Screen options={{ headerShown: false, title: '登录与安全' }} />
      <ScrollView contentContainerStyle={styles.content} contentInsetAdjustmentBehavior="automatic" showsVerticalScrollIndicator={false}>
        <View style={styles.nav}><Pressable onPress={() => router.back()} style={styles.navButton} accessibilityRole="button" accessibilityLabel="返回"><Ionicons name="chevron-back" size={22} color={colors.ink} /></Pressable><Text style={styles.navTitle}>登录与安全</Text><View style={styles.navSpacer} /></View>
        <View style={styles.hero}><View style={styles.heroIcon}><Ionicons name="shield-checkmark" size={28} color={colors.forest} /></View><Text style={styles.heroTitle}>账户安全状态良好</Text><Text style={styles.heroText}>每次登录都需要手机号短信验证，访问令牌只保存在本机安全存储中。</Text><StatusPill label="已保护" icon="checkmark-circle-outline" /></View>
        <Text style={styles.sectionTitle}>登录方式</Text>
        <View style={styles.panel}><InfoRow icon="call-outline" title="手机号" value={session?.user.phone || '--'} /><InfoRow icon="chatbubble-ellipses-outline" title="短信验证" value="阿里云通道" last /></View>
        <Text style={styles.sectionTitle}>设备访问</Text>
        <View style={styles.panel}><InfoRow icon="phone-portrait-outline" title="当前设备" value="本机 APP" /><InfoRow icon="key-outline" title="会话有效期" value="30 天滚动更新" last /></View>
        <View style={styles.notice}><Ionicons name="information-circle-outline" size={18} color={colors.leaf} /><Text style={styles.noticeText}>这是独立 APP 账户体系，不会关联微信 OpenID 或旧小程序登录记录。</Text></View>
      </ScrollView>
    </SafeAreaView>
  );
}

function InfoRow({ icon, title, value, last = false }: { icon: keyof typeof Ionicons.glyphMap; title: string; value: string; last?: boolean }) {
  return <View style={[styles.row, last && styles.rowLast]}><View style={styles.rowIcon}><Ionicons name={icon} size={17} color={colors.forest} /></View><Text style={styles.rowTitle}>{title}</Text><Text style={styles.rowValue} selectable>{value}</Text></View>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.canvas },
  content: { paddingHorizontal: 20, paddingBottom: 38, gap: 17 },
  nav: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  navButton: { width: 40, height: 40, borderRadius: 13, backgroundColor: colors.surface, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line },
  navSpacer: { width: 40, height: 40 },
  navTitle: { color: colors.ink, fontSize: 16, fontWeight: '700' },
  hero: { backgroundColor: colors.forest, borderRadius: radii.lg, padding: 20, alignItems: 'flex-start' },
  heroIcon: { width: 56, height: 56, borderRadius: 19, backgroundColor: colors.lime, alignItems: 'center', justifyContent: 'center', marginBottom: 17 },
  heroTitle: { color: colors.white, fontSize: 20, fontWeight: '700' },
  heroText: { color: '#bed0ba', fontSize: 12, lineHeight: 19, marginTop: 7, marginBottom: 15 },
  sectionTitle: { color: colors.ink, fontSize: 15, fontWeight: '700', marginTop: 1 },
  panel: { backgroundColor: colors.surface, borderRadius: radii.md, borderWidth: 1, borderColor: colors.line, paddingHorizontal: 14 },
  row: { minHeight: 65, borderBottomWidth: 1, borderBottomColor: '#edf1eb', flexDirection: 'row', alignItems: 'center', gap: 10 },
  rowLast: { borderBottomWidth: 0 },
  rowIcon: { width: 35, height: 35, borderRadius: 11, backgroundColor: '#e5efdc', alignItems: 'center', justifyContent: 'center' },
  rowTitle: { color: colors.inkSoft, fontSize: 12, flex: 1 },
  rowValue: { color: colors.ink, fontSize: 12, fontWeight: '700' },
  notice: { backgroundColor: '#e9f1e4', borderRadius: 14, padding: 13, flexDirection: 'row', gap: 8, alignItems: 'flex-start' },
  noticeText: { color: colors.inkSoft, fontSize: 11, lineHeight: 17, flex: 1 },
});
