import { Ionicons } from '@expo/vector-icons';
import * as ImagePicker from 'expo-image-picker';
import { Stack, useRouter } from 'expo-router';
import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Image, Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { StatusPill } from '../components/StatusPill';
import { colors, radii, shadow } from '../components/theme';
import { useAuth } from '../context/AuthContext';
import { api, WalletPayload, WithdrawalPayload } from '../lib/api';

type Method = 'bank_card' | 'wechat' | 'alipay';
const METHODS: Array<{ key: Method; label: string; icon: keyof typeof Ionicons.glyphMap }> = [
  { key: 'bank_card', label: '银行卡', icon: 'card-outline' },
  { key: 'wechat', label: '微信', icon: 'logo-wechat' },
  { key: 'alipay', label: '支付宝', icon: 'wallet-outline' },
];

export default function WalletScreen() {
  const router = useRouter();
  const { session } = useAuth();
  const token = session?.token;

  const [wallet, setWallet] = useState<WalletPayload | null>(null);
  const [withdrawals, setWithdrawals] = useState<WithdrawalPayload[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState<Method>('bank_card');
  const [accountName, setAccountName] = useState('');
  const [accountNo, setAccountNo] = useState('');
  const [qrUrl, setQrUrl] = useState('');
  const [qrUploading, setQrUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    if (!token) return;
    try {
      setLoading(true);
      setError('');
      const [w, wd] = await Promise.all([api.getWallet(token), api.listWithdrawals(token)]);
      setWallet(w);
      setWithdrawals(wd || []);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { void load(); }, [load]);

  const balance = Number(wallet?.balance || 0);

  const submit = async () => {
    const amt = parseFloat(amount);
    if (!Number.isFinite(amt) || amt <= 0) { setMessage('请输入正确的提现金额'); return; }
    if (amt > balance) { setMessage('可用余额不足'); return; }
    if (method === 'bank_card') {
      if (!accountName.trim()) { setMessage('请填写持卡人姓名'); return; }
      if (!accountNo.trim()) { setMessage('请填写银行卡号'); return; }
    } else {
      if (!accountName.trim()) { setMessage('请填写收款人姓名'); return; }
      if (!qrUrl) { setMessage('请上传微信/支付宝收款码'); return; }
    }
    try {
      setSubmitting(true);
      setMessage('');
      await api.withdraw(token!, {
        amount: amt,
        method,
        accountName: accountName.trim() || undefined,
        accountNo: method === 'bank_card' ? accountNo.trim() : undefined,
        qrCodeUrl: method === 'bank_card' ? undefined : qrUrl,
      });
      setMessage('提现申请已提交，等待审核');
      setAmount('');
      setAccountNo('');
      setAccountName('');
      setQrUrl('');
      await load();
    } catch (e) {
      setMessage(e instanceof Error ? e.message : '提交失败，请重试');
    } finally {
      setSubmitting(false);
    }
  };

  const pickQr = async () => {
    try {
      const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!perm.granted) { setMessage('需要相册权限才能选择收款码'); return; }
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ImagePicker.MediaTypeOptions.Images,
        allowsEditing: false,
        quality: 0.85,
      });
      if (result.canceled || !result.assets?.length) return;
      const asset = result.assets[0];
      setQrUploading(true);
      setMessage('');
      const data = await api.uploadQr(token!, asset.uri);
      setQrUrl(data.url);
      setMessage('收款码已上传');
    } catch (e) {
      setMessage(e instanceof Error ? e.message : '上传失败，请重试');
    } finally {
      setQrUploading(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <Stack.Screen options={{ headerShown: false, title: '钱包与提现' }} />
      <ScrollView
        contentContainerStyle={styles.content}
        contentInsetAdjustmentBehavior="automatic"
        showsVerticalScrollIndicator={false}
        refreshControl={<RefreshControl refreshing={loading} onRefresh={load} tintColor={colors.accent} />}
      >
        <View style={styles.nav}>
          <Pressable onPress={() => router.back()} style={styles.navButton} accessibilityRole="button" accessibilityLabel="返回"><Ionicons name="chevron-back" size={22} color={colors.ink} /></Pressable>
          <Text style={styles.navTitle}>钱包与提现</Text>
          <View style={styles.navSpacer} />
        </View>

        <View style={styles.hero}>
          <View style={styles.heroTop}><Text style={styles.heroLabel}>可提现余额 (元)</Text><View style={styles.heroIcon}><Ionicons name="wallet-outline" size={20} color={colors.highlight} /></View></View>
          <Text style={styles.heroValue} selectable>{formatCurrency(balance)}</Text>
          <Text style={styles.heroHint}>余额 = 已结算收益 − 已提现/兑换</Text>
        </View>

        {!!error && <View style={styles.banner}><Ionicons name="cloud-offline-outline" size={16} color={colors.danger} /><Text style={styles.bannerText}>{error}</Text></View>}

        <Text style={styles.sectionTitle}>申请提现</Text>
        <View style={styles.form}>
          <Text style={styles.fieldLabel}>提现金额</Text>
          <TextInput
            style={styles.input}
            value={amount}
            onChangeText={setAmount}
            keyboardType="decimal-pad"
            placeholder="请输入金额"
            placeholderTextColor={colors.inkFaint}
            editable={!submitting}
          />
          <View style={styles.quickRow}>
            <QuickAmount label="全部提现" onPress={() => setAmount(balance > 0 ? String(balance) : '')} />
            <QuickAmount label="提现 100" onPress={() => setAmount('100')} />
            <QuickAmount label="提现 500" onPress={() => setAmount('500')} />
          </View>

          <Text style={styles.fieldLabel}>收款方式</Text>
          <View style={styles.segment}>
            {METHODS.map((m) => (
              <Pressable key={m.key} onPress={() => setMethod(m.key)} style={[styles.segmentItem, method === m.key && styles.segmentItemActive]} accessibilityRole="button">
                <Ionicons name={m.icon} size={16} color={method === m.key ? colors.brand : colors.inkFaint} />
                <Text style={[styles.segmentText, method === m.key && styles.segmentTextActive]}>{m.label}</Text>
              </Pressable>
            ))}
          </View>

          {method === 'bank_card' ? (
            <>
              <Text style={styles.fieldLabel}>持卡人姓名</Text>
              <TextInput style={styles.input} value={accountName} onChangeText={setAccountName} placeholder="请输入持卡人姓名" placeholderTextColor={colors.inkFaint} editable={!submitting} />
              <Text style={styles.fieldLabel}>银行卡号</Text>
              <TextInput style={styles.input} value={accountNo} onChangeText={setAccountNo} keyboardType="number-pad" placeholder="请输入银行卡号" placeholderTextColor={colors.inkFaint} editable={!submitting} />
            </>
          ) : (
            <>
              <Text style={styles.fieldLabel}>收款人姓名</Text>
              <TextInput style={styles.input} value={accountName} onChangeText={setAccountName} placeholder="请输入收款人姓名" placeholderTextColor={colors.inkFaint} editable={!submitting} />
              <Text style={styles.fieldLabel}>收款码</Text>
              {qrUrl ? (
                <View style={styles.qrPreview}>
                  <Image source={{ uri: qrUrl }} style={styles.qrImage} resizeMode="contain" />
                  <Pressable onPress={pickQr} disabled={qrUploading} style={styles.qrChangeBtn} accessibilityRole="button"><Text style={styles.qrChangeText}>重新上传</Text></Pressable>
                </View>
              ) : (
                <Pressable onPress={pickQr} disabled={qrUploading} style={styles.qrUploadBtn} accessibilityRole="button" accessibilityLabel="上传收款码">
                  {qrUploading ? <ActivityIndicator color={colors.brand} /> : <><Ionicons name="image-outline" size={22} color={colors.brand} /><Text style={styles.qrUploadText}>上传{method === 'wechat' ? '微信' : '支付宝'}收款码</Text></>}
                </Pressable>
              )}
              <Text style={styles.qrHint}>请上传清晰的收款码图片，打款时将扫码转账。</Text>
            </>
          )}

          {!!message && <Text style={[styles.message, message.includes('成功') || message.includes('提交') ? styles.messageOk : styles.messageErr]}>{message}</Text>}

          <Pressable onPress={submit} disabled={submitting} style={({ pressed }) => [styles.submit, (pressed || submitting) && styles.pressed]} accessibilityRole="button" accessibilityLabel="提交提现申请">
            {submitting ? <ActivityIndicator color={colors.white} /> : <><Text style={styles.submitText}>提交提现申请</Text><Ionicons name="arrow-forward" size={17} color={colors.white} /></>}
          </Pressable>
          <Text style={styles.formNote}><Ionicons name="information-circle-outline" size={13} color={colors.inkFaint} /> 提交后由平台审核打款，到账前可在此页查看进度。</Text>
        </View>

        <Text style={styles.sectionTitle}>提现记录</Text>
        {withdrawals.length ? (
          <View style={styles.list}>
            {withdrawals.map((w, i) => <WithdrawalRow key={w.id} withdrawal={w} last={i === withdrawals.length - 1} />)}
          </View>
        ) : (
          <View style={styles.empty}><Text style={styles.emptyText}>暂无提现记录</Text></View>
        )}

        <Text style={styles.sectionTitle}>钱包明细</Text>
        {wallet?.ledger?.length ? (
          <View style={styles.list}>
            {wallet.ledger.slice(0, 20).map((l, i) => (
              <View key={l.id} style={[styles.ledgerRow, i === Math.min(wallet.ledger.length, 20) - 1 && styles.rowLast]}>
                <View style={styles.ledgerIcon}><Ionicons name={l.direction === 'credit' ? 'arrow-down' : 'arrow-up'} size={15} color={l.direction === 'credit' ? colors.brand : colors.danger} /></View>
                <View style={styles.ledgerCopy}><Text style={styles.ledgerTitle}>{l.description || l.entry_type}</Text><Text style={styles.ledgerDate}>{formatDate(l.created_at)}</Text></View>
                <Text style={[styles.ledgerAmount, l.direction === 'credit' ? styles.credit : styles.debit]} selectable>{l.direction === 'credit' ? '+' : '-'}{formatCurrency(Number(l.amount))}</Text>
              </View>
            ))}
          </View>
        ) : (
          <View style={styles.empty}><Text style={styles.emptyText}>暂无钱包明细</Text></View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function QuickAmount({ label, onPress }: { label: string; onPress: () => void }) {
  return <Pressable onPress={onPress} style={styles.quickBtn} accessibilityRole="button"><Text style={styles.quickText}>{label}</Text></Pressable>;
}

function WithdrawalRow({ withdrawal: w, last }: { withdrawal: WithdrawalPayload; last: boolean }) {
  return (
    <View style={[styles.ledgerRow, last && styles.rowLast]}>
      <View style={styles.ledgerIcon}><Ionicons name="paper-plane-outline" size={15} color={colors.brand} /></View>
      <View style={styles.ledgerCopy}><Text style={styles.ledgerTitle}>提现 · {methodLabel(w.method)}</Text><Text style={styles.ledgerDate}>{formatDate(w.created_at)}</Text></View>
      <View style={styles.ledgerAmountWrap}><Text style={styles.ledgerAmount} selectable>{formatCurrency(Number(w.amount))}</Text><StatusPill label={statusLabel(w.status)} tone={w.status === 'pending' ? 'pending' : w.status === 'approved' || w.status === 'paid' ? 'positive' : 'danger'} /></View>
    </View>
  );
}

function methodLabel(method: string): string {
  return ({ bank_card: '银行卡', wechat: '微信', alipay: '支付宝' } as Record<string, string>)[method] || method;
}

function statusLabel(status: string): string {
  return ({ pending: '审核中', approved: '已通过', paid: '已打款', rejected: '已驳回' } as Record<string, string>)[status] || status;
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
  safe: { flex: 1, backgroundColor: colors.canvas },
  content: { paddingHorizontal: 20, paddingBottom: 42, gap: 16 },
  nav: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  navButton: { width: 40, height: 40, borderRadius: 13, backgroundColor: colors.surface, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: colors.line },
  navSpacer: { width: 40, height: 40 },
  navTitle: { color: colors.ink, fontSize: 16, fontWeight: '700' },
  hero: { backgroundColor: colors.brand, borderRadius: radii.lg, padding: 20, ...shadow.soft },
  heroTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  heroLabel: { color: '#A9BBD4', fontSize: 12 },
  heroIcon: { width: 40, height: 40, borderRadius: 13, backgroundColor: 'rgba(255,255,255,0.14)', alignItems: 'center', justifyContent: 'center' },
  heroValue: { color: colors.white, fontSize: 38, fontWeight: '700', marginTop: 12, fontVariant: ['tabular-nums'] },
  heroHint: { color: '#8593A6', fontSize: 11, marginTop: 9 },
  banner: { backgroundColor: '#FDF1F0', borderColor: '#F4D3D0', borderWidth: 1, borderRadius: 12, padding: 11, flexDirection: 'row', alignItems: 'center', gap: 7 },
  bannerText: { color: colors.danger, fontSize: 11, flex: 1 },
  sectionTitle: { color: colors.ink, fontSize: 15, fontWeight: '700', marginTop: 2 },
  form: { backgroundColor: colors.surface, borderRadius: radii.md, ...shadow.card, padding: 16, gap: 11 },
  fieldLabel: { color: colors.inkSoft, fontSize: 12, fontWeight: '600', marginTop: 2 },
  input: { minHeight: 48, borderWidth: 1, borderColor: colors.line, borderRadius: 12, paddingHorizontal: 14, color: colors.ink, fontSize: 15, backgroundColor: colors.canvas },
  qrUploadBtn: { minHeight: 96, borderRadius: 14, borderWidth: 1.5, borderStyle: 'dashed', borderColor: colors.accent, backgroundColor: '#F0F3F7', alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 8 },
  qrUploadText: { color: colors.brand, fontSize: 14, fontWeight: '700' },
  qrHint: { color: colors.inkFaint, fontSize: 11, lineHeight: 16 },
  qrPreview: { alignItems: 'center', gap: 10 },
  qrImage: { width: 160, height: 160, borderRadius: 12, backgroundColor: colors.canvas, borderWidth: 1, borderColor: colors.line },
  qrChangeBtn: { minHeight: 34, paddingHorizontal: 18, borderRadius: 10, borderWidth: 1, borderColor: colors.line, backgroundColor: '#F0F3F7', alignItems: 'center', justifyContent: 'center' },
  qrChangeText: { color: colors.brand, fontSize: 12, fontWeight: '600' },
  quickRow: { flexDirection: 'row', gap: 8 },
  quickBtn: { flex: 1, minHeight: 34, borderRadius: 10, borderWidth: 1, borderColor: colors.line, backgroundColor: '#F0F3F7', alignItems: 'center', justifyContent: 'center' },
  quickText: { color: colors.brand, fontSize: 11, fontWeight: '600' },
  segment: { flexDirection: 'row', gap: 8 },
  segmentItem: { flex: 1, minHeight: 44, borderRadius: 12, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.canvas, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 6 },
  segmentItemActive: { borderColor: colors.accent, backgroundColor: '#F0F3F7' },
  segmentText: { color: colors.inkFaint, fontSize: 13, fontWeight: '600' },
  segmentTextActive: { color: colors.brand },
  message: { fontSize: 12, lineHeight: 17 },
  messageOk: { color: colors.accent },
  messageErr: { color: colors.danger },
  submit: { minHeight: 50, borderRadius: 14, backgroundColor: colors.brand, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 8, marginTop: 3 },
  submitText: { color: colors.white, fontSize: 15, fontWeight: '700' },
  pressed: { opacity: 0.8 },
  formNote: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  list: { backgroundColor: colors.surface, borderRadius: radii.md, ...shadow.card, paddingHorizontal: 14 },
  ledgerRow: { minHeight: 66, borderBottomWidth: 1, borderBottomColor: '#F0F3F7', flexDirection: 'row', alignItems: 'center' },
  rowLast: { borderBottomWidth: 0 },
  ledgerIcon: { width: 36, height: 36, borderRadius: 12, backgroundColor: '#E8EEF7', alignItems: 'center', justifyContent: 'center' },
  ledgerCopy: { flex: 1, marginLeft: 10 },
  ledgerTitle: { color: colors.ink, fontSize: 13, fontWeight: '600' },
  ledgerDate: { color: colors.inkFaint, fontSize: 10, marginTop: 4 },
  ledgerAmount: { fontSize: 14, fontWeight: '700', fontVariant: ['tabular-nums'] },
  credit: { color: colors.brand },
  debit: { color: colors.danger },
  ledgerAmountWrap: { alignItems: 'flex-end', gap: 5 },
  empty: { backgroundColor: colors.surface, borderRadius: radii.md, ...shadow.card, padding: 26, alignItems: 'center' },
  emptyText: { color: colors.inkFaint, fontSize: 12 },
});
