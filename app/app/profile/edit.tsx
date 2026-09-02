import { Ionicons } from '@expo/vector-icons';
import { Stack, useRouter } from 'expo-router';
import React, { useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { colors, radii } from '../../components/theme';
import { useAuth } from '../../context/AuthContext';

export default function EditProfileScreen() {
  const router = useRouter();
  const { session, updateNickname } = useAuth();
  const [nickname, setNickname] = useState(session?.user.nickname || 'Orin 用户');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const save = async () => {
    const next = nickname.trim();
    if (!next) {
      setError('昵称不能为空');
      return;
    }
    if (next.length > 40) {
      setError('昵称不能超过 40 个字符');
      return;
    }
    if (saving) return;
    setSaving(true);
    setError('');
    try {
      await updateNickname(next);
      router.back();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '保存失败，请稍后重试');
    } finally {
      setSaving(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <Stack.Screen options={{ headerShown: false, title: '个人资料' }} />
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.content} contentInsetAdjustmentBehavior="automatic" keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
          <View style={styles.nav}><Pressable onPress={() => router.back()} style={styles.navButton} accessibilityRole="button" accessibilityLabel="返回"><Ionicons name="chevron-back" size={22} color={colors.ink} /></Pressable><Text style={styles.navTitle}>个人资料</Text><View style={styles.navSpacer} /></View>

          <View style={styles.avatar}><Ionicons name="person" size={31} color={colors.forest} /></View>
          <Text style={styles.pageTitle}>让账户更像你</Text>
          <Text style={styles.pageSubtitle}>昵称只用于 APP 内展示，不会影响短信登录。</Text>

          <View style={styles.form}><Text style={styles.label}>昵称</Text><View style={[styles.inputShell, error && styles.inputError]}><Ionicons name="person-outline" size={19} color={error ? colors.coral : colors.inkFaint} /><TextInput value={nickname} onChangeText={(value) => { setNickname(value.slice(0, 40)); setError(''); }} placeholder="输入昵称" placeholderTextColor={colors.inkFaint} style={styles.input} maxLength={40} returnKeyType="done" onSubmitEditing={save} /></View><Text style={styles.counter}>{nickname.length}/40</Text>{error ? <View style={styles.error}><Ionicons name="alert-circle-outline" size={16} color={colors.coral} /><Text style={styles.errorText}>{error}</Text></View> : null}</View>

          <View style={styles.accountCard}><View style={styles.cardRow}><Ionicons name="call-outline" size={17} color={colors.leaf} /><Text style={styles.cardLabel}>登录手机号</Text><Text style={styles.cardValue} selectable>{session?.user.phone || '--'}</Text></View><View style={styles.cardRule} /><View style={styles.cardRow}><Ionicons name="shield-checkmark-outline" size={17} color={colors.leaf} /><Text style={styles.cardLabel}>验证方式</Text><Text style={styles.cardValue}>阿里云短信</Text></View></View>

          <Pressable onPress={save} disabled={saving} style={[styles.saveButton, saving && styles.disabled]} accessibilityRole="button" accessibilityLabel="保存个人资料"><Text style={styles.saveText}>{saving ? '保存中…' : '保存资料'}</Text>{saving ? <ActivityIndicator color={colors.white} /> : <Ionicons name="checkmark" size={19} color={colors.white} />}</Pressable>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.canvas },
  flex: { flex: 1 },
  content: { paddingHorizontal: 20, paddingBottom: 35, gap: 16 },
  nav: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  navButton: { width: 40, height: 40, borderRadius: 13, backgroundColor: colors.surface, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line },
  navSpacer: { width: 40, height: 40 },
  navTitle: { color: colors.ink, fontSize: 16, fontWeight: '700' },
  avatar: { width: 72, height: 72, borderRadius: 24, backgroundColor: colors.lime, alignItems: 'center', justifyContent: 'center', alignSelf: 'center', marginTop: 17 },
  pageTitle: { color: colors.ink, fontSize: 23, fontWeight: '700', textAlign: 'center', marginTop: 2 },
  pageSubtitle: { color: colors.inkSoft, fontSize: 12, textAlign: 'center', marginTop: -8 },
  form: { marginTop: 13 },
  label: { color: colors.ink, fontSize: 13, fontWeight: '700', marginBottom: 9 },
  inputShell: { minHeight: 55, borderRadius: 14, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.surface, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 15 },
  inputError: { borderColor: '#eabdb5', backgroundColor: '#fffafa' },
  input: { flex: 1, color: colors.ink, fontSize: 16, marginLeft: 11, paddingVertical: 0 },
  counter: { color: colors.inkFaint, fontSize: 10, textAlign: 'right', marginTop: 6 },
  error: { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 6 },
  errorText: { color: colors.coral, fontSize: 11 },
  accountCard: { backgroundColor: colors.surface, borderRadius: radii.md, borderWidth: 1, borderColor: colors.line, paddingHorizontal: 14, paddingVertical: 4 },
  cardRow: { minHeight: 57, flexDirection: 'row', alignItems: 'center', gap: 9 },
  cardLabel: { color: colors.inkSoft, fontSize: 12, flex: 1 },
  cardValue: { color: colors.ink, fontSize: 12, fontWeight: '700' },
  cardRule: { height: 1, backgroundColor: '#edf1eb' },
  saveButton: { minHeight: 54, borderRadius: 15, backgroundColor: colors.forest, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 9, marginTop: 3 },
  saveText: { color: colors.white, fontSize: 14, fontWeight: '700' },
  disabled: { opacity: 0.6 },
});
