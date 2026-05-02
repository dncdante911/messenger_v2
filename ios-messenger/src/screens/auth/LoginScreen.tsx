import React, { useState, useRef, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  StatusBar,
  SafeAreaView,
  KeyboardAvoidingView,
  ScrollView,
  Platform,
  ActivityIndicator,
  Animated,
  Dimensions,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { AuthStackParamList } from '../../navigation/types';
import { useAuthStore } from '../../store/authStore';

type LoginNavigationProp = NativeStackNavigationProp<AuthStackParamList, 'Login'>;

const { width } = Dimensions.get('window');
const LOGO_SIZE = width * 0.2;

export function LoginScreen() {
  const navigation = useNavigation<LoginNavigationProp>();

  const isLoading = useAuthStore((state) => state.isLoading);
  const error = useAuthStore((state) => state.error);
  const login = useAuthStore((state) => state.login);
  const clearError = useAuthStore((state) => state.clearError);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);

  const errorOpacity = useRef(new Animated.Value(0)).current;
  const passwordRef = useRef<TextInput>(null);

  useEffect(() => {
    return () => {
      clearError();
    };
  }, [clearError]);

  useEffect(() => {
    if (error || localError) {
      Animated.timing(errorOpacity, {
        toValue: 1,
        duration: 300,
        useNativeDriver: true,
      }).start();
    } else {
      errorOpacity.setValue(0);
    }
  }, [error, localError, errorOpacity]);

  const validate = (): boolean => {
    if (!email.trim()) {
      setLocalError('Email or phone number is required.');
      return false;
    }
    if (!password) {
      setLocalError('Password is required.');
      return false;
    }
    setLocalError(null);
    return true;
  };

  const handleLogin = async () => {
    clearError();
    setLocalError(null);
    if (!validate()) return;
    try {
      await login(email.trim().toLowerCase(), password);
    } catch {
      // Error is set in the store
    }
  };

  const displayError = localError || error;

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor="#1A1B2E" />
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        keyboardVerticalOffset={0}
      >
        <ScrollView
          style={styles.flex}
          contentContainerStyle={styles.scrollContent}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.logoSection}>
            <View style={styles.logoCircle}>
              <Text style={styles.logoText}>WM</Text>
            </View>
            <Text style={styles.appName}>WorldMates</Text>
            <Text style={styles.appTagline}>Connect. Share. Belong.</Text>
          </View>

          <View style={styles.formContainer}>
            <Text style={styles.welcomeTitle}>Welcome back</Text>
            <Text style={styles.welcomeSubtitle}>Sign in to continue</Text>

            {displayError ? (
              <Animated.View style={[styles.errorBanner, { opacity: errorOpacity }]}>
                <Text style={styles.errorBannerText}>{displayError}</Text>
              </Animated.View>
            ) : null}

            <View style={styles.inputWrapper}>
              <Text style={styles.inputLabel}>Email or Phone</Text>
              <TextInput
                style={styles.input}
                placeholder="Enter your email or phone"
                placeholderTextColor="#4A4E6A"
                value={email}
                onChangeText={(text) => {
                  setEmail(text);
                  if (localError) setLocalError(null);
                  if (error) clearError();
                }}
                keyboardType="email-address"
                autoCapitalize="none"
                autoCorrect={false}
                autoComplete="email"
                returnKeyType="next"
                onSubmitEditing={() => passwordRef.current?.focus()}
                selectionColor="#7C83FD"
              />
            </View>

            <View style={styles.inputWrapper}>
              <Text style={styles.inputLabel}>Password</Text>
              <View style={styles.passwordContainer}>
                <TextInput
                  ref={passwordRef}
                  style={styles.passwordInput}
                  placeholder="Enter your password"
                  placeholderTextColor="#4A4E6A"
                  value={password}
                  onChangeText={(text) => {
                    setPassword(text);
                    if (localError) setLocalError(null);
                    if (error) clearError();
                  }}
                  secureTextEntry={!showPassword}
                  autoCapitalize="none"
                  autoCorrect={false}
                  autoComplete="current-password"
                  returnKeyType="done"
                  onSubmitEditing={handleLogin}
                  selectionColor="#7C83FD"
                />
                <TouchableOpacity
                  style={styles.eyeButton}
                  onPress={() => setShowPassword((v) => !v)}
                  hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                >
                  <Text style={styles.eyeIcon}>{showPassword ? '🙈' : '👁️'}</Text>
                </TouchableOpacity>
              </View>
            </View>

            <TouchableOpacity
              style={styles.forgotPassword}
              onPress={() => navigation.navigate('ForgotPassword')}
              activeOpacity={0.7}
            >
              <Text style={styles.forgotPasswordText}>Forgot password?</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.signInButton, isLoading && styles.signInButtonDisabled]}
              onPress={handleLogin}
              activeOpacity={0.85}
              disabled={isLoading}
            >
              {isLoading ? (
                <ActivityIndicator color="#FFFFFF" size="small" />
              ) : (
                <Text style={styles.signInButtonText}>Sign In</Text>
              )}
            </TouchableOpacity>
          </View>
        </ScrollView>

        <View style={styles.footer}>
          <Text style={styles.footerText}>Don't have an account? </Text>
          <TouchableOpacity onPress={() => navigation.navigate('Register')} activeOpacity={0.7}>
            <Text style={styles.footerLink}>Register</Text>
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#1A1B2E',
  },
  flex: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    paddingHorizontal: 24,
    paddingBottom: 16,
  },
  logoSection: {
    alignItems: 'center',
    paddingTop: 48,
    paddingBottom: 36,
  },
  logoCircle: {
    width: LOGO_SIZE,
    height: LOGO_SIZE,
    borderRadius: LOGO_SIZE / 2,
    backgroundColor: '#7C83FD',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 14,
    shadowColor: '#7C83FD',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 12,
    elevation: 8,
  },
  logoText: {
    color: '#FFFFFF',
    fontSize: LOGO_SIZE * 0.36,
    fontWeight: '800',
    letterSpacing: 1,
  },
  appName: {
    color: '#FFFFFF',
    fontSize: 24,
    fontWeight: '700',
    letterSpacing: 0.3,
    marginBottom: 4,
  },
  appTagline: {
    color: '#8A8FA8',
    fontSize: 13,
  },
  formContainer: {
    flex: 1,
  },
  welcomeTitle: {
    color: '#FFFFFF',
    fontSize: 26,
    fontWeight: '700',
    marginBottom: 6,
  },
  welcomeSubtitle: {
    color: '#8A8FA8',
    fontSize: 15,
    marginBottom: 28,
  },
  errorBanner: {
    backgroundColor: 'rgba(255, 80, 80, 0.15)',
    borderWidth: 1,
    borderColor: 'rgba(255, 80, 80, 0.4)',
    borderRadius: 10,
    padding: 12,
    marginBottom: 20,
  },
  errorBannerText: {
    color: '#FF6B6B',
    fontSize: 14,
    fontWeight: '500',
    textAlign: 'center',
  },
  inputWrapper: {
    marginBottom: 18,
  },
  inputLabel: {
    color: '#8A8FA8',
    fontSize: 13,
    fontWeight: '600',
    marginBottom: 8,
    letterSpacing: 0.3,
  },
  input: {
    backgroundColor: '#2A2B3D',
    borderRadius: 12,
    paddingVertical: 14,
    paddingHorizontal: 16,
    color: '#FFFFFF',
    fontSize: 16,
    borderWidth: 1,
    borderColor: '#3A3B52',
  },
  passwordContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#2A2B3D',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#3A3B52',
  },
  passwordInput: {
    flex: 1,
    paddingVertical: 14,
    paddingHorizontal: 16,
    color: '#FFFFFF',
    fontSize: 16,
  },
  eyeButton: {
    paddingHorizontal: 14,
    paddingVertical: 14,
  },
  eyeIcon: {
    fontSize: 18,
  },
  forgotPassword: {
    alignSelf: 'flex-end',
    marginBottom: 28,
  },
  forgotPasswordText: {
    color: '#7C83FD',
    fontSize: 14,
    fontWeight: '500',
  },
  signInButton: {
    backgroundColor: '#7C83FD',
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: 'center',
    shadowColor: '#7C83FD',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 12,
    elevation: 6,
    minHeight: 52,
    justifyContent: 'center',
  },
  signInButtonDisabled: {
    opacity: 0.7,
  },
  signInButtonText: {
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 20,
    paddingBottom: 28,
  },
  footerText: {
    color: '#8A8FA8',
    fontSize: 15,
  },
  footerLink: {
    color: '#7C83FD',
    fontSize: 15,
    fontWeight: '600',
  },
});
