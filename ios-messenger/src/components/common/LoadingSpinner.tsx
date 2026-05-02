// ============================================================
// WorldMates Messenger — LoadingSpinner
// Reusable activity indicator component.
// ============================================================

import React from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

interface LoadingSpinnerProps {
  /** 'small' or 'large'. Defaults to 'large'. */
  size?: 'small' | 'large';
  /** Spinner colour. Defaults to the app accent #7C83FD. */
  color?: string;
  /**
   * When true, the spinner is absolutely positioned over its parent,
   * occupying the full available space with a semi-transparent dark background.
   */
  fullscreen?: boolean;
  /** Optional label rendered below the spinner. */
  text?: string;
}

const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  size = 'large',
  color = '#7C83FD',
  fullscreen = false,
  text,
}) => {
  if (fullscreen) {
    return (
      <View style={styles.fullscreen}>
        <ActivityIndicator size={size} color={color} />
        {text ? <Text style={styles.text}>{text}</Text> : null}
      </View>
    );
  }

  return (
    <View style={styles.inline}>
      <ActivityIndicator size={size} color={color} />
      {text ? <Text style={styles.text}>{text}</Text> : null}
    </View>
  );
};

const styles = StyleSheet.create({
  fullscreen: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#1A1B2E99',
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
    color: '#8E8E93',
    fontSize: 14,
  },
});

export default LoadingSpinner;
