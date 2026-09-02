import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import React, { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { StatusPill } from '../components/StatusPill';
import { colors, radii, shadow } from '../components/theme';
import { useDevices } from '../context/DeviceContext';

export default function AddDeviceScreen() {
  const router = useRouter();
  const { addDevice, mutating } = useDevices();
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    const normalized = code.trim().toUpperCase();
    if (!/^[A-Z0-9-]{6,64}$/.test(normalized)) {
      setError('请输入 6-64 位设备绑定码（字母、数字或短横线）');
      return;
    }
    setError('');
    if (saving || mutating) return;
    setSaving(true);
    try {
      await addDevice(normalized, name);
      router.back();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '绑定失败，请稍后重试');
    } finally {
      setSaving(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.content} contentInsetAdjustmentBehavior="automatic" keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
          <View style={styles.nav}><Pressable onPress={() => router.back()} style={styles.navButton} accessibilityRole="button" accessibilityLabel="关闭"><Ionicons name="close" size={22} color={colors.ink} /></Pressable><Text style={styles.navTitle}>绑定节点</Text><View style={styles.navSpacer} /></View>
          <View style={styles.hero}><View style={styles.heroIcon}><Ionicons name="hardware-chip-outline" size={30} color={colors.lime} /></View><View style={styles.heroCopy}><Text style={styles.heroKicker}>ADD A NODE</Text><Text style={styles.heroTitle}>连接你的 Orin</Text><Text style={styles.heroSubtitle}>绑定后可在设备页查看节点状态与收益。</Text></View></View>

          <View style={styles.steps}><Step index="01" title="找到绑定码" detail="在节点管理页面或设备标签上查看。" /><Step index="02" title="输入并保存" detail="绑定码只用于新 APP 账户。" /><Step index="03" title="等待上线" detail="节点首次上报后会显示实时状态。" last /></View>

          <View style={styles.form}><Text style={styles.label}>设备名称 <Text style={styles.optional}>可选</Text></Text><View style={styles.inputShell}><Ionicons name="pricetag-outline" size={18} color={colors.inkFaint} /><TextInput value={name} onChangeText={(value) => setName(value.slice(0, 40))} placeholder="例如：客厅节点" placeholderTextColor={colors.inkFaint} style={styles.input} returnKeyType="next" /></View><Text style={styles.label}>绑定码</Text><View style={[styles.inputShell, error && styles.inputError]}><Ionicons name="key-outline" size={18} color={error ? colors.coral : colors.inkFaint} /><TextInput value={code} onChangeText={(value) => { setCode(value.replace(/[^a-zA-Z0-9-]/g, '').slice(0, 64)); setError(''); }} placeholder="输入设备绑定码" placeholderTextColor={colors.inkFaint} autoCapitalize="characters" autoCorrect={false} style={styles.input} maxLength={64} /></View>{error ? <View style={styles.error}><Ionicons name="alert-circle-outline" size={16} color={colors.coral} /><Text style={styles.errorText}>{error}</Text></View> : <Text style={styles.helper}>绑定码由运营后台预置，保存后会在设备页显示。</Text>}</View>

          <View style={styles.notice}><StatusPill label="新账户体系" tone="positive" icon="shield-checkmark-outline" /><Text style={styles.noticeText}>不会导入旧小程序的设备、收益或余额数据。</Text></View>
          <Pressable onPress={submit} disabled={saving || mutating} style={[styles.submit, (saving || mutating) && styles.disabled]} accessibilityRole="button" accessibilityLabel="保存绑定信息"><Text style={styles.submitText}>{saving || mutating ? '绑定中…' : '保存绑定信息'}</Text><Ionicons name="arrow-forward" size={18} color={colors.white} /></Pressable>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function Step({ index, title, detail, last = false }: { index: string; title: string; detail: string; last?: boolean }) {
  return <View style={[styles.step, last && styles.stepLast]}><Text style={styles.stepIndex}>{index}</Text><View style={styles.stepCopy}><Text style={styles.stepTitle}>{title}</Text><Text style={styles.stepDetail}>{detail}</Text></View></View>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.canvas }, flex: { flex: 1 }, content: { paddingHorizontal: 20, paddingBottom: 28, gap: 18 },
  nav: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, navButton: { width: 40, height: 40, borderRadius: 13, backgroundColor: colors.surface, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line }, navSpacer: { width: 40, height: 40 }, navTitle: { color: colors.ink, fontSize: 16, fontWeight: '700' },
  hero: { backgroundColor: colors.forest, borderRadius: radii.lg, padding: 19, flexDirection: 'row', alignItems: 'center', ...shadow.soft }, heroIcon: { width: 58, height: 58, borderRadius: 19, backgroundColor: 'rgba(214,237,155,0.16)', alignItems: 'center', justifyContent: 'center' }, heroCopy: { flex: 1, marginLeft: 14 }, heroKicker: { color: '#b9ceb2', fontSize: 10, fontWeight: '700', letterSpacing: 1.7, marginBottom: 6 }, heroTitle: { color: colors.white, fontSize: 20, fontWeight: '700' }, heroSubtitle: { color: '#bbceb6', fontSize: 11, lineHeight: 17, marginTop: 5 },
  steps: { backgroundColor: colors.surface, borderRadius: radii.md, paddingHorizontal: 15, borderWidth: 1, borderColor: colors.line }, step: { minHeight: 62, flexDirection: 'row', alignItems: 'center', borderBottomWidth: 1, borderBottomColor: '#edf1eb' }, stepLast: { borderBottomWidth: 0 }, stepIndex: { color: colors.leaf, fontSize: 11, fontWeight: '800', letterSpacing: 1 }, stepCopy: { flex: 1, marginLeft: 13 }, stepTitle: { color: colors.ink, fontSize: 13, fontWeight: '700' }, stepDetail: { color: colors.inkFaint, fontSize: 11, marginTop: 4 },
  form: { gap: 9 }, label: { color: colors.ink, fontSize: 13, fontWeight: '700', marginTop: 2 }, optional: { color: colors.inkFaint, fontSize: 11, fontWeight: '400' }, inputShell: { minHeight: 53, borderRadius: 14, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.surface, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 15 }, inputError: { borderColor: '#eabdb5', backgroundColor: '#fffafa' }, input: { flex: 1, marginLeft: 10, color: colors.ink, fontSize: 15, paddingVertical: 0 }, helper: { color: colors.inkFaint, fontSize: 10, lineHeight: 16, marginTop: -2 }, error: { flexDirection: 'row', alignItems: 'flex-start', gap: 6, marginTop: -2 }, errorText: { color: colors.coral, fontSize: 11, lineHeight: 17, flex: 1 },
  notice: { backgroundColor: '#e9f1e4', borderRadius: 14, padding: 13, gap: 8 }, noticeText: { color: colors.inkSoft, fontSize: 11, lineHeight: 17 }, submit: { minHeight: 54, borderRadius: 15, backgroundColor: colors.forest, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 9, marginTop: 2 }, submitText: { color: colors.white, fontSize: 14, fontWeight: '700' }, pressed: { opacity: 0.76, transform: [{ scale: 0.985 }] }, disabled: { opacity: 0.6 },
});
