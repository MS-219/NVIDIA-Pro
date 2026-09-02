import { Ionicons } from '@expo/vector-icons';
import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from './theme';

type StatusPillProps = {
  label: string;
  tone?: 'positive' | 'pending' | 'muted' | 'danger';
  icon?: keyof typeof Ionicons.glyphMap;
};

export function StatusPill({ label, tone = 'positive', icon = 'pulse-outline' }: StatusPillProps) {
  return (
    <View style={[styles.pill, styles[tone]]}>
      <Ionicons name={icon} size={12} color={tone === 'positive' ? colors.forest : tone === 'danger' ? colors.coral : colors.inkSoft} />
      <Text style={[styles.text, tone === 'positive' && styles.textPositive, tone === 'danger' && styles.textDanger]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  pill: { minHeight: 28, borderRadius: 14, paddingHorizontal: 10, flexDirection: 'row', alignItems: 'center', gap: 5 },
  positive: { backgroundColor: '#e2f0d6' },
  pending: { backgroundColor: '#f5ecd8' },
  muted: { backgroundColor: colors.surfaceMuted },
  danger: { backgroundColor: '#f8e2de' },
  text: { color: colors.inkSoft, fontSize: 11, fontWeight: '700' },
  textPositive: { color: colors.forest },
  textDanger: { color: colors.coral },
});
