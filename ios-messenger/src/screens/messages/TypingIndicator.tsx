// ============================================================
// WorldMates Messenger — TypingIndicator
// Shows an animated "Name is typing..." row with 3 bouncing dots.
// ============================================================

import React, { useEffect, useRef } from 'react';
import {
  View,
  Text,
  Animated,
  StyleSheet,
} from 'react-native';

// ─────────────────────────────────────────────────────────────
// PROPS
// ─────────────────────────────────────────────────────────────

interface TypingIndicatorProps {
  userName: string;
  isVisible: boolean;
}

// ─────────────────────────────────────────────────────────────
// CONSTANTS
// ─────────────────────────────────────────────────────────────

const DOT_COUNT = 3;
const DOT_ANIMATION_DURATION = 400;
const DOT_STAGGER_DELAY = 200;
const CONTAINER_HEIGHT = 32;

// ─────────────────────────────────────────────────────────────
// COMPONENT
// ─────────────────────────────────────────────────────────────

const TypingIndicator: React.FC<TypingIndicatorProps> = ({ userName, isVisible }) => {
  // Height slide animation
  const heightAnim = useRef(new Animated.Value(0)).current;
  const opacityAnim = useRef(new Animated.Value(0)).current;

  // Dot bounce animations
  const dotAnims = useRef<Animated.Value[]>(
    Array.from({ length: DOT_COUNT }, () => new Animated.Value(0)),
  ).current;

  // Looping dot bounce animation
  const dotLoopRef = useRef<Animated.CompositeAnimation | null>(null);

  const startDotAnimation = () => {
    dotLoopRef.current?.stop();

    const sequence = Animated.loop(
      Animated.stagger(
        DOT_STAGGER_DELAY,
        dotAnims.map((anim) =>
          Animated.sequence([
            Animated.timing(anim, {
              toValue: -6,
              duration: DOT_ANIMATION_DURATION / 2,
              useNativeDriver: true,
            }),
            Animated.timing(anim, {
              toValue: 0,
              duration: DOT_ANIMATION_DURATION / 2,
              useNativeDriver: true,
            }),
          ]),
        ),
      ),
    );

    dotLoopRef.current = sequence;
    sequence.start();
  };

  const stopDotAnimation = () => {
    dotLoopRef.current?.stop();
    dotLoopRef.current = null;
    dotAnims.forEach((anim) => anim.setValue(0));
  };

  useEffect(() => {
    if (isVisible) {
      // Slide in + fade in
      Animated.parallel([
        Animated.timing(heightAnim, {
          toValue: CONTAINER_HEIGHT,
          duration: 180,
          useNativeDriver: false,
        }),
        Animated.timing(opacityAnim, {
          toValue: 1,
          duration: 180,
          useNativeDriver: false,
        }),
      ]).start();
      startDotAnimation();
    } else {
      // Slide out + fade out
      Animated.parallel([
        Animated.timing(heightAnim, {
          toValue: 0,
          duration: 150,
          useNativeDriver: false,
        }),
        Animated.timing(opacityAnim, {
          toValue: 0,
          duration: 150,
          useNativeDriver: false,
        }),
      ]).start(() => stopDotAnimation());
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isVisible]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      dotLoopRef.current?.stop();
    };
  }, []);

  return (
    <Animated.View style={[styles.container, { height: heightAnim, opacity: opacityAnim }]}>
      {/* Avatar placeholder */}
      <View style={styles.avatarCircle} />

      {/* Label */}
      <Text style={styles.label} numberOfLines={1}>
        {userName} is typing
      </Text>

      {/* Animated dots */}
      <View style={styles.dotsRow}>
        {dotAnims.map((anim, index) => (
          <Animated.View
            key={index}
            style={[styles.dot, { transform: [{ translateY: anim }] }]}
          />
        ))}
      </View>
    </Animated.View>
  );
};

// ─────────────────────────────────────────────────────────────
// STYLES
// ─────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 4,
    overflow: 'hidden',
    backgroundColor: 'transparent',
  },
  avatarCircle: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: '#3A3B4E',
    marginRight: 8,
    flexShrink: 0,
  },
  label: {
    color: '#8E8E93',
    fontSize: 13,
    marginRight: 6,
    flexShrink: 1,
  },
  dotsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
  },
  dot: {
    width: 5,
    height: 5,
    borderRadius: 2.5,
    backgroundColor: '#7C83FD',
  },
});

export default TypingIndicator;
