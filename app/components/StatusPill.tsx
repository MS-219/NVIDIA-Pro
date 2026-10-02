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
      <Ionicons name={icon} size={12} color={tone === 'positive' ? colors.accent : tone === 'danger' ? colors.danger : colors.inkSoft} />
      <Text style={[styles.text, tone === 'positive' && styles.textPositive, tone === 'danger' && styles.textDanger]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  pill: { minHeight: 28, borderRadius: 14, paddingHorizontal: 10, flexDirection: 'row', alignItems: 'center', gap: 5 },
  positive: { backgroundColor: '#E3F1EA' },
  pending: { backgroundColor: '#FBF0DC' },
  muted: { backgroundColor: colors.surfaceMuted },
  danger: { backgroundColor: '#FDF1F0' },
  text: { color: colors.inkSoft, fontSize: 11, fontWeight: '700' },
  textPositive: { color: colors.accent },
  textDanger: { color: colors.danger },
});
