// ============================================================
// WorldMates Messenger — Unread Count Badge
//
// Pill badge displayed on chat list items.
// Hidden when count === 0.
// Displays "99+" when count exceeds 99.
// Gray when muted, accent (#7C83FD) otherwise.
// ============================================================

import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

export interface BadgeProps {
  count: number;
  muted?: boolean;
}

export const Badge: React.FC<BadgeProps> = ({ count, muted = false }) => {
  if (count === 0) return null;

  const label = count > 99 ? '99+' : String(count);
  const isWide = count > 9;

  return (
    <View
      style={[
        styles.pill,
        { backgroundColor: muted ? '#8E8E93' : '#7C83FD' },
        isWide && styles.pillWide,
      ]}
    >
      <Text style={styles.label}>{label}</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  pill: {
    height: 20,
    minWidth: 20,
    borderRadius: 10,
    paddingHorizontal: 5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pillWide: {
    paddingHorizontal: 6,
  },
  label: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '600',
    lineHeight: 14,
    includeFontPadding: false,
    textAlignVertical: 'center',
  },
});
