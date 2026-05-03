import React, { useEffect, useRef } from 'react';
import { View, Text, Animated, StyleSheet } from 'react-native';
import { useTheme } from '../../theme';
import { useTranslation } from '../../i18n';

interface TypingIndicatorProps {
  userName: string;
  isVisible: boolean;
}

const DOT_COUNT = 3;
const DOT_ANIMATION_DURATION = 400;
const DOT_STAGGER_DELAY = 200;
const CONTAINER_HEIGHT = 32;

const TypingIndicator: React.FC<TypingIndicatorProps> = ({ userName, isVisible }) => {
  const theme = useTheme();
  const { t } = useTranslation();

  const heightAnim = useRef(new Animated.Value(0)).current;
  const opacityAnim = useRef(new Animated.Value(0)).current;
  const dotAnims = useRef<Animated.Value[]>(
    Array.from({ length: DOT_COUNT }, () => new Animated.Value(0)),
  ).current;
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

  useEffect(() => {
    return () => {
      dotLoopRef.current?.stop();
    };
  }, []);

  return (
    <Animated.View style={[styles.container, { height: heightAnim, opacity: opacityAnim }]}>
      <View style={[styles.avatarCircle, { backgroundColor: theme.surface }]} />
      <Text style={[styles.label, { color: theme.textSecondary }]} numberOfLines={1}>
        {userName} {t('is_typing')}
      </Text>
      <View style={styles.dotsRow}>
        {dotAnims.map((anim, index) => (
          <Animated.View
            key={index}
            style={[styles.dot, { backgroundColor: theme.primary, transform: [{ translateY: anim }] }]}
          />
        ))}
      </View>
    </Animated.View>
  );
};

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
    marginRight: 8,
    flexShrink: 0,
  },
  label: { fontSize: 13, marginRight: 6, flexShrink: 1 },
  dotsRow: { flexDirection: 'row', alignItems: 'center', gap: 3 },
  dot: { width: 5, height: 5, borderRadius: 2.5 },
});

export default TypingIndicator;
