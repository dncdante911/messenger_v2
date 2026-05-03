import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../theme';

export interface BadgeProps {
  count: number;
  muted?: boolean;
}

export const Badge: React.FC<BadgeProps> = ({ count, muted = false }) => {
  const theme = useTheme();

  if (count === 0) return null;

  const label = count > 99 ? '99+' : String(count);
  const isWide = count > 9;

  return (
    <View
      style={[
        styles.pill,
        { backgroundColor: muted ? theme.textTertiary : theme.primary },
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
