import React from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../../theme';

interface LoadingSpinnerProps {
  size?: 'small' | 'large';
  color?: string;
  fullscreen?: boolean;
  text?: string;
}

const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  size = 'large',
  color,
  fullscreen = false,
  text,
}) => {
  const theme = useTheme();
  const spinnerColor = color ?? theme.primary;

  if (fullscreen) {
    return (
      <View style={[styles.fullscreen, { backgroundColor: theme.background + 'CC' }]}>
        <ActivityIndicator size={size} color={spinnerColor} />
        {text ? <Text style={[styles.text, { color: theme.textSecondary }]}>{text}</Text> : null}
      </View>
    );
  }

  return (
    <View style={styles.inline}>
      <ActivityIndicator size={size} color={spinnerColor} />
      {text ? <Text style={[styles.text, { color: theme.textSecondary }]}>{text}</Text> : null}
    </View>
  );
};

const styles = StyleSheet.create({
  fullscreen: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 999,
  },
  inline: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  text: {
    marginTop: 10,
    fontSize: 14,
  },
});

export default LoadingSpinner;
