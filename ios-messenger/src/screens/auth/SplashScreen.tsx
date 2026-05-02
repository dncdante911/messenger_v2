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

type SplashNavigationProp = NativeStackNavigationProp<AuthStackParamList, 'Splash'>;

const { width } = Dimensions.get('window');
const LOGO_SIZE = width * 0.32;

export function SplashScreen() {
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
    <View style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#1A1B2E" />
      <Animated.View
        style={[
          styles.logoContainer,
          {
            transform: [{ scale: scaleAnim }],
            opacity: opacityAnim,
          },
        ]}
      >
        <View style={styles.logoCircle}>
          <Text style={styles.logoText}>WM</Text>
        </View>
        <View style={styles.logoGlow} pointerEvents="none" />
      </Animated.View>

      <Animated.View style={{ opacity: textOpacityAnim, alignItems: 'center' }}>
        <Text style={styles.appName}>WorldMates</Text>
        <Text style={styles.tagline}>Connect. Share. Belong.</Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#1A1B2E',
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
    backgroundColor: '#7C83FD',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#7C83FD',
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
    backgroundColor: 'rgba(124, 131, 253, 0.15)',
  },
  logoText: {
    color: '#FFFFFF',
    fontSize: LOGO_SIZE * 0.38,
    fontWeight: '800',
    letterSpacing: 2,
  },
  appName: {
    color: '#FFFFFF',
    fontSize: 32,
    fontWeight: '700',
    letterSpacing: 0.5,
    marginBottom: 8,
  },
  tagline: {
    color: '#8A8FA8',
    fontSize: 15,
    fontWeight: '400',
    letterSpacing: 0.3,
  },
});
