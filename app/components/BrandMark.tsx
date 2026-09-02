import { Ionicons } from '@expo/vector-icons';
import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from './theme';

type BrandMarkProps = {
  compact?: boolean;
  dark?: boolean;
};

export function BrandMark({ compact = false, dark = false }: BrandMarkProps) {
  return (
    <View style={styles.row}>
      <View style={[styles.mark, compact && styles.markCompact, dark && styles.markDark]}>
        <Ionicons name="hardware-chip-outline" size={compact ? 18 : 22} color={dark ? colors.ink : colors.white} />
      </View>
      {!compact && <Text style={[styles.wordmark, dark && styles.wordmarkDark]}>聚芯节点</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  mark: { width: 40, height: 40, borderRadius: 13, backgroundColor: colors.forest, alignItems: 'center', justifyContent: 'center' },
  markCompact: { width: 34, height: 34, borderRadius: 11 },
  markDark: { backgroundColor: colors.lime },
  wordmark: { color: colors.ink, fontSize: 18, fontWeight: '700', letterSpacing: 0.2 },
  wordmarkDark: { color: colors.white },
});
