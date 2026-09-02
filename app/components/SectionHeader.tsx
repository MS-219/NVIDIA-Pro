import { Ionicons } from '@expo/vector-icons';
import { Link } from 'expo-router';
import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors } from './theme';

type SectionHeaderProps = {
  title: string;
  subtitle?: string;
  href?: string;
  actionLabel?: string;
};

export function SectionHeader({ title, subtitle, href, actionLabel = '查看全部' }: SectionHeaderProps) {
  const content = (
    <View style={styles.action}>
      <Text style={styles.actionText}>{actionLabel}</Text>
      <Ionicons name="arrow-forward" size={14} color={colors.leaf} />
    </View>
  );

  return (
    <View style={styles.row}>
      <View>
        <Text style={styles.title}>{title}</Text>
        {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
      </View>
      {href ? (
        <Link href={href as never} asChild>
          <Pressable accessibilityRole="button" accessibilityLabel={actionLabel} hitSlop={8}>
            {content}
          </Pressable>
        </Link>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { minHeight: 32, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { color: colors.ink, fontSize: 16, fontWeight: '700' },
  subtitle: { color: colors.inkFaint, fontSize: 12, marginTop: 4 },
  action: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  actionText: { color: colors.leaf, fontSize: 12, fontWeight: '700' },
});
