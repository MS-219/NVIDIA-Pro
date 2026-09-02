import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import React from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { StatusPill } from '../components/StatusPill';
import { colors, radii } from '../components/theme';

export default function NotificationsScreen() {
  const router = useRouter();
  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <ScrollView contentContainerStyle={styles.content} contentInsetAdjustmentBehavior="automatic" showsVerticalScrollIndicator={false}>
        <View style={styles.nav}><Pressable onPress={() => router.back()} style={styles.navButton} accessibilityRole="button" accessibilityLabel="返回"><Ionicons name="chevron-back" size={22} color={colors.ink} /></Pressable><Text style={styles.navTitle}>平台通知</Text><View style={styles.navSpacer} /></View>
        <View style={styles.header}><View><Text style={styles.kicker}>UPDATES</Text><Text style={styles.title}>消息</Text></View><StatusPill label="0 条未读" tone="muted" icon="mail-open-outline" /></View>
        <View style={styles.empty}><View style={styles.emptyIcon}><Ionicons name="notifications-off-outline" size={30} color={colors.forest} /></View><Text style={styles.emptyTitle}>暂时没有新消息</Text><Text style={styles.emptyText}>设备状态、结算和服务维护通知会显示在这里。</Text></View>
        <View style={styles.info}><View style={styles.infoIcon}><Ionicons name="information-circle-outline" size={18} color={colors.leaf} /></View><View style={styles.infoCopy}><Text style={styles.infoTitle}>通知服务已准备</Text><Text style={styles.infoText}>新 APP 后端会为你的账户保留独立的通知记录。</Text></View></View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.canvas }, content: { paddingHorizontal: 20, paddingBottom: 38, gap: 18 },
  nav: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, navButton: { width: 40, height: 40, borderRadius: 13, backgroundColor: colors.surface, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line }, navSpacer: { width: 40, height: 40 }, navTitle: { color: colors.ink, fontSize: 16, fontWeight: '700' },
  header: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, kicker: { color: colors.leaf, fontSize: 10, fontWeight: '700', letterSpacing: 1.8, marginBottom: 6 }, title: { color: colors.ink, fontSize: 29, fontWeight: '700' },
  empty: { backgroundColor: colors.surface, borderRadius: radii.md, borderWidth: 1, borderColor: colors.line, alignItems: 'center', paddingHorizontal: 24, paddingVertical: 38 }, emptyIcon: { width: 62, height: 62, borderRadius: 21, backgroundColor: '#e5efdc', alignItems: 'center', justifyContent: 'center', marginBottom: 16 }, emptyTitle: { color: colors.ink, fontSize: 17, fontWeight: '700' }, emptyText: { color: colors.inkSoft, fontSize: 12, lineHeight: 19, textAlign: 'center', maxWidth: 275, marginTop: 8 },
  info: { backgroundColor: '#e9f1e4', borderRadius: 14, padding: 14, flexDirection: 'row', alignItems: 'flex-start', gap: 9 }, infoIcon: { width: 28, height: 28, borderRadius: 9, backgroundColor: colors.surface, alignItems: 'center', justifyContent: 'center' }, infoCopy: { flex: 1 }, infoTitle: { color: colors.ink, fontSize: 12, fontWeight: '700' }, infoText: { color: colors.inkSoft, fontSize: 11, lineHeight: 17, marginTop: 3 },
});
