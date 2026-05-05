import React, { useEffect, useRef } from 'react';
import {
  View,
  Text,
  Animated,
  StyleSheet,
  StatusBar,
  Dimensions,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { AuthStackParamList } from '../../navigation/types';
import { storageService } from '../../services/storageService';
import { useTranslation, useI18nStore } from '../../i18n';
import { useTheme, useThemeStore } from '../../theme';

type SplashNavigationProp = NativeStackNavigationProp<AuthStackParamList, 'Splash'>;

const { width } = Dimensions.get('window');
const LOGO_SIZE = width * 0.32;

export function SplashScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const navigation = useNavigation<SplashNavigationProp>();
  const scaleAnim = useRef(new Animated.Value(0.4)).current;
  const opacityAnim = useRef(new Animated.Value(0)).current;
  const textOpacityAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.spring(scaleAnim, {
        toValue: 1,
        friction: 6,
        tension: 80,
        useNativeDriver: true,
      }),
      Animated.timing(opacityAnim, {
        toValue: 1,
        duration: 500,
        useNativeDriver: true,
      }),
    ]).start();

    Animated.timing(textOpacityAnim, {
      toValue: 1,
      duration: 600,
      delay: 400,
      useNativeDriver: true,
    }).start();

    const timer = setTimeout(async () => {
      // Hydrate language and theme before any screen renders
      await Promise.all([
        useI18nStore.getState()._hydrate(),
        useThemeStore.getState()._hydrate(),
      ]);
      const isFirst = await storageService.isFirstLaunch();
      if (isFirst) {
        navigation.replace('LanguageSelection');
      } else {
        navigation.replace('Login');
      }
    }, 2000);

    return () => clearTimeout(timer);
  }, [navigation, scaleAnim, opacityAnim, textOpacityAnim]);

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <StatusBar barStyle="light-content" backgroundColor={theme.background} />
      <Animated.View
        style={[
          styles.logoContainer,
          {
            transform: [{ scale: scaleAnim }],
            opacity: opacityAnim,
          },
        ]}
      >
        <View style={[styles.logoCircle, { backgroundColor: theme.primary, shadowColor: theme.primary }]}>
          <Text style={[styles.logoText, { color: theme.white }]}>WM</Text>
        </View>
        <View style={[styles.logoGlow, { backgroundColor: 'rgba(124, 131, 253, 0.15)' }]} pointerEvents="none" />
      </Animated.View>

      <Animated.View style={{ opacity: textOpacityAnim, alignItems: 'center' }}>
        <Text style={[styles.appName, { color: theme.text }]}>{t('app_name')}</Text>
        <Text style={[styles.tagline, { color: theme.textTertiary }]}>{t('login_world_subtitle')}</Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 28,
  },
  logoContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  logoCircle: {
    width: LOGO_SIZE,
    height: LOGO_SIZE,
    borderRadius: LOGO_SIZE / 2,
    alignItems: 'center',
    justifyContent: 'center',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.6,
    shadowRadius: 24,
    elevation: 12,
  },
  logoGlow: {
    position: 'absolute',
    width: LOGO_SIZE + 32,
    height: LOGO_SIZE + 32,
    borderRadius: (LOGO_SIZE + 32) / 2,
  },
  logoText: {
    fontSize: LOGO_SIZE * 0.38,
    fontWeight: '800',
    letterSpacing: 2,
  },
  appName: {
    fontSize: 32,
    fontWeight: '700',
    letterSpacing: 0.5,
    marginBottom: 8,
  },
  tagline: {
    fontSize: 15,
    fontWeight: '400',
    letterSpacing: 0.3,
  },
});
