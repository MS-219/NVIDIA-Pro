import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import React, { useEffect, useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { BrandMark } from '../components/BrandMark';
import { colors, radii, shadow } from '../components/theme';
import { useAuth } from '../context/AuthContext';
import { apiConfigMissing, API_BASE_URL } from '../lib/config';
import { api } from '../lib/api';

const PHONE_PATTERN = /^1[3-9]\d{9}$/;

export default function LoginScreen() {
  const router = useRouter();
  const { session, loading: authLoading, signIn } = useAuth();
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [countdown, setCountdown] = useState(0);
  const [sending, setSending] = useState(false);
  const [loggingIn, setLoggingIn] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (session && !authLoading) router.replace('/(tabs)/home');
  }, [authLoading, router, session]);

  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setInterval(() => setCountdown((value) => value - 1), 1000);
    return () => clearInterval(timer);
  }, [countdown]);

  const sendCode = async () => {
    setError('');
    if (!PHONE_PATTERN.test(phone)) {
      setError('请输入有效的 11 位手机号');
      return;
    }
    if (countdown > 0 || sending) return;
    setSending(true);
    try {
      const result = await api.sendLoginCode(phone);
      setCountdown(Math.max(Number(result.retryAfterSeconds) || 60, 1));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '验证码发送失败');
    } finally {
      setSending(false);
    }
  };

  const login = async () => {
    setError('');
    if (!PHONE_PATTERN.test(phone)) {
      setError('请输入有效的 11 位手机号');
      return;
    }
    if (!/^\d{6}$/.test(code)) {
      setError('请输入 6 位验证码');
      return;
    }
    setLoggingIn(true);
    try {
      await signIn(phone, code);
      router.replace('/(tabs)/home');
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '登录失败，请重试');
    } finally {
      setLoggingIn(false);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea} edges={['top', 'bottom']}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.container} contentInsetAdjustmentBehavior="automatic" keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
          <View style={styles.brandRow}><BrandMark /><View style={styles.secureTag}><Ionicons name="shield-checkmark-outline" size={13} color={colors.leaf} /><Text style={styles.secureText}>安全登录</Text></View></View>

          <View style={styles.hero}><Text style={styles.eyebrow}>ORIN NODE NETWORK</Text><Text style={styles.title}>欢迎回来</Text><Text style={styles.subtitle}>使用手机号登录全新的聚芯节点 APP</Text></View>

          <View style={styles.form}>
            <Text style={styles.label}>手机号</Text>
            <View style={styles.inputShell}><Ionicons name="call-outline" size={19} color={colors.inkFaint} /><TextInput value={phone} onChangeText={(value) => setPhone(value.replace(/\D/g, '').slice(0, 11))} placeholder="请输入手机号" placeholderTextColor={colors.inkFaint} keyboardType="phone-pad" textContentType="telephoneNumber" style={styles.input} maxLength={11} /></View>
            <Text style={[styles.label, styles.codeLabel]}>短信验证码</Text>
            <View style={styles.inputShell}><Ionicons name="shield-checkmark-outline" size={19} color={colors.inkFaint} /><TextInput value={code} onChangeText={(value) => setCode(value.replace(/\D/g, '').slice(0, 6))} placeholder="请输入 6 位验证码" placeholderTextColor={colors.inkFaint} keyboardType="number-pad" textContentType="oneTimeCode" style={styles.input} maxLength={6} /><Pressable onPress={sendCode} disabled={sending || countdown > 0} style={styles.codeButton} accessibilityRole="button" accessibilityLabel="获取短信验证码">{sending ? <ActivityIndicator size="small" color={colors.leaf} /> : <Text style={styles.codeButtonText}>{countdown > 0 ? `${countdown}s` : '获取验证码'}</Text>}</Pressable></View>
            {!!error && <View style={styles.errorRow}><Ionicons name="alert-circle-outline" size={17} color={colors.coral} /><Text style={styles.errorText} selectable>{error}</Text></View>}
            {apiConfigMissing && <View style={styles.configNotice}><Ionicons name="construct-outline" size={15} color={colors.amber} /><Text style={styles.configText}>当前使用本机开发 API：{API_BASE_URL}</Text></View>}
            <Pressable onPress={login} disabled={loggingIn} style={({ pressed }) => [styles.loginButton, pressed && styles.buttonPressed, loggingIn && styles.buttonDisabled]} accessibilityRole="button" accessibilityLabel="登录"><Text style={styles.loginButtonText}>{loggingIn ? '登录中…' : '登录'}</Text>{loggingIn ? <ActivityIndicator color={colors.white} /> : <Ionicons name="arrow-forward" size={19} color={colors.white} />}</Pressable>
            <Text style={styles.agreement}>登录即表示你同意服务协议与隐私政策</Text>
          </View>

          <View style={styles.featureStrip}><Feature icon="lock-closed-outline" label="加密传输" /><Feature icon="chatbubble-ellipses-outline" label="短信验证" /><Feature icon="server-outline" label="独立服务" /></View>
          <View style={styles.footer}><View style={styles.footerLine} /><Text style={styles.footerText}>SECURE ACCESS · SMS VERIFIED</Text><View style={styles.footerLine} /></View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function Feature({ icon, label }: { icon: keyof typeof Ionicons.glyphMap; label: string }) {
  return <View style={styles.feature}><Ionicons name={icon} size={15} color={colors.leaf} /><Text style={styles.featureText}>{label}</Text></View>;
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.canvas }, flex: { flex: 1 }, container: { flexGrow: 1, paddingHorizontal: 24, paddingTop: 25, paddingBottom: 22 }, brandRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }, secureTag: { minHeight: 28, borderRadius: 14, paddingHorizontal: 10, backgroundColor: '#e6efdf', flexDirection: 'row', alignItems: 'center', gap: 5 }, secureText: { color: colors.leaf, fontSize: 11, fontWeight: '700' }, hero: { marginTop: 74, marginBottom: 37 }, eyebrow: { color: colors.leaf, fontSize: 11, fontWeight: '700', letterSpacing: 2.4, marginBottom: 14 }, title: { color: colors.ink, fontSize: 36, fontWeight: '700', letterSpacing: 0.2 }, subtitle: { color: colors.inkSoft, fontSize: 15, marginTop: 12, lineHeight: 22 }, form: { width: '100%' }, label: { color: colors.ink, fontSize: 13, fontWeight: '700', marginBottom: 9 }, codeLabel: { marginTop: 22 }, inputShell: { minHeight: 56, borderRadius: 14, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.surface, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, minWidth: 0 }, input: { flex: 1, minWidth: 0, color: colors.ink, fontSize: 16, marginLeft: 11, paddingVertical: 0 }, codeButton: { minWidth: 72, flexShrink: 1, alignItems: 'flex-end', justifyContent: 'center', paddingLeft: 6 }, codeButtonText: { color: colors.leaf, fontSize: 12, fontWeight: '700' }, errorRow: { flexDirection: 'row', alignItems: 'center', marginTop: 14, gap: 6 }, errorText: { color: colors.coral, fontSize: 13, flex: 1 }, configNotice: { flexDirection: 'row', alignItems: 'flex-start', gap: 6, marginTop: 13, padding: 10, borderRadius: 11, backgroundColor: '#f7edda' }, configText: { color: '#8d6b31', fontSize: 10, lineHeight: 15, flex: 1 }, loginButton: { minHeight: 56, borderRadius: 14, backgroundColor: colors.forest, marginTop: 25, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 10, ...shadow.soft }, loginButtonText: { color: colors.white, fontSize: 16, fontWeight: '700' }, buttonPressed: { opacity: 0.82, transform: [{ scale: 0.985 }] }, buttonDisabled: { opacity: 0.65 }, agreement: { textAlign: 'center', color: colors.inkFaint, fontSize: 11, marginTop: 16 }, featureStrip: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 'auto', paddingTop: 32, paddingBottom: 17 }, feature: { flexDirection: 'row', alignItems: 'center', gap: 5 }, featureText: { color: colors.inkFaint, fontSize: 10 }, footer: { flexDirection: 'row', alignItems: 'center', gap: 10 }, footerLine: { flex: 1, height: 1, backgroundColor: colors.line }, footerText: { color: '#a2ada3', fontSize: 9, letterSpacing: 1.2 },
});
