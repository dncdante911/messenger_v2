import React, { useState, useRef } from 'react';
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
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { AuthStackParamList } from '../../navigation/types';

type ForgotPasswordNavigationProp = NativeStackNavigationProp<
  AuthStackParamList,
  'ForgotPassword'
>;

export function ForgotPasswordScreen() {
  const navigation = useNavigation<ForgotPasswordNavigationProp>();

  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const successOpacity = useRef(new Animated.Value(0)).current;
  const errorOpacity = useRef(new Animated.Value(0)).current;

  const validateEmail = (value: string): boolean => {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  };

  const handleSend = async () => {
    setError(null);

    if (!email.trim()) {
      setError('Email address is required.');
      Animated.timing(errorOpacity, {
        toValue: 1,
        duration: 300,
        useNativeDriver: true,
      }).start();
      return;
    }

    if (!validateEmail(email.trim())) {
      setError('Please enter a valid email address.');
      Animated.timing(errorOpacity, {
        toValue: 1,
        duration: 300,
        useNativeDriver: true,
      }).start();
      return;
    }

    setIsLoading(true);

    try {
      // Simulate API call — real implementation would call authApi.forgotPassword(email)
      await new Promise<void>((resolve, reject) => {
        setTimeout(() => {
          // Succeed for valid-looking emails
          resolve();
        }, 1200);
      });

      setIsLoading(false);
      setIsSuccess(true);

      Animated.timing(successOpacity, {
        toValue: 1,
        duration: 400,
        useNativeDriver: true,
      }).start();
    } catch (err: any) {
      setIsLoading(false);
      const message =
        err?.response?.data?.message ||
        err?.message ||
        'Failed to send reset link. Please try again.';
      setError(message);
      Animated.timing(errorOpacity, {
        toValue: 1,
        duration: 300,
        useNativeDriver: true,
      }).start();
    }
  };

  const handleEmailChange = (text: string) => {
    setEmail(text);
    if (error) {
      setError(null);
      errorOpacity.setValue(0);
    }
  };

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
          <TouchableOpacity
            style={styles.backButton}
            onPress={() => navigation.goBack()}
            hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}
            activeOpacity={0.7}
          >
            <Text style={styles.backArrow}>←</Text>
            <Text style={styles.backText}>Back</Text>
          </TouchableOpacity>

          {!isSuccess ? (
            <>
              <View style={styles.iconContainer}>
                <View style={styles.iconCircle}>
                  <Text style={styles.iconEmoji}>🔒</Text>
                </View>
              </View>

              <View style={styles.header}>
                <Text style={styles.title}>Forgot Password?</Text>
                <Text style={styles.subtitle}>
                  No worries! Enter your email address and we'll send you a link to reset your
                  password.
                </Text>
              </View>

              {error ? (
                <Animated.View style={[styles.errorBanner, { opacity: errorOpacity }]}>
                  <Text style={styles.errorBannerText}>{error}</Text>
                </Animated.View>
              ) : null}

              <View style={styles.inputWrapper}>
                <Text style={styles.inputLabel}>Email Address</Text>
                <TextInput
                  style={[styles.input, error ? styles.inputError : null]}
                  placeholder="Enter your email"
                  placeholderTextColor="#4A4E6A"
                  value={email}
                  onChangeText={handleEmailChange}
                  keyboardType="email-address"
                  autoCapitalize="none"
                  autoCorrect={false}
                  autoComplete="email"
                  returnKeyType="done"
                  onSubmitEditing={handleSend}
                  selectionColor="#7C83FD"
                  autoFocus
                />
              </View>

              <TouchableOpacity
                style={[styles.sendButton, isLoading && styles.sendButtonDisabled]}
                onPress={handleSend}
                activeOpacity={0.85}
                disabled={isLoading}
              >
                {isLoading ? (
                  <ActivityIndicator color="#FFFFFF" size="small" />
                ) : (
                  <Text style={styles.sendButtonText}>Send Reset Link</Text>
                )}
              </TouchableOpacity>
            </>
          ) : (
            <Animated.View style={[styles.successContainer, { opacity: successOpacity }]}>
              <View style={styles.successIconCircle}>
                <Text style={styles.successCheckmark}>✓</Text>
              </View>
              <Text style={styles.successTitle}>Check your email</Text>
              <Text style={styles.successMessage}>
                We've sent a password reset link to{'\n'}
                <Text style={styles.successEmail}>{email.trim().toLowerCase()}</Text>
              </Text>
              <Text style={styles.successHint}>
                Didn't receive the email? Check your spam folder or try again.
              </Text>

              <TouchableOpacity
                style={styles.resendButton}
                onPress={() => {
                  setIsSuccess(false);
                  successOpacity.setValue(0);
                  setEmail('');
                }}
                activeOpacity={0.7}
              >
                <Text style={styles.resendButtonText}>Try a different email</Text>
              </TouchableOpacity>
            </Animated.View>
          )}

          <View style={styles.footer}>
            <Text style={styles.footerText}>Remember your password? </Text>
            <TouchableOpacity
              onPress={() => navigation.navigate('Login')}
              activeOpacity={0.7}
            >
              <Text style={styles.footerLink}>Sign In</Text>
            </TouchableOpacity>
          </View>
        </ScrollView>
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
    paddingBottom: 32,
  },
  backButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingTop: 20,
    paddingBottom: 8,
    gap: 6,
  },
  backArrow: {
    color: '#7C83FD',
    fontSize: 20,
    fontWeight: '500',
  },
  backText: {
    color: '#7C83FD',
    fontSize: 16,
    fontWeight: '500',
  },
  iconContainer: {
    alignItems: 'center',
    paddingTop: 32,
    paddingBottom: 24,
  },
  iconCircle: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: '#2A2B3D',
    borderWidth: 2,
    borderColor: '#7C83FD',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#7C83FD',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.3,
    shadowRadius: 16,
    elevation: 8,
  },
  iconEmoji: {
    fontSize: 36,
  },
  header: {
    paddingBottom: 28,
  },
  title: {
    color: '#FFFFFF',
    fontSize: 26,
    fontWeight: '700',
    marginBottom: 10,
  },
  subtitle: {
    color: '#8A8FA8',
    fontSize: 15,
    lineHeight: 22,
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
    marginBottom: 24,
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
  inputError: {
    borderColor: '#FF5C5C',
  },
  sendButton: {
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
  sendButtonDisabled: {
    opacity: 0.7,
  },
  sendButtonText: {
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  successContainer: {
    flex: 1,
    alignItems: 'center',
    paddingTop: 40,
    paddingBottom: 24,
  },
  successIconCircle: {
    width: 100,
    height: 100,
    borderRadius: 50,
    backgroundColor: 'rgba(76, 175, 130, 0.15)',
    borderWidth: 2,
    borderColor: '#4CAF82',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 28,
    shadowColor: '#4CAF82',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.3,
    shadowRadius: 16,
    elevation: 8,
  },
  successCheckmark: {
    color: '#4CAF82',
    fontSize: 44,
    fontWeight: '700',
  },
  successTitle: {
    color: '#FFFFFF',
    fontSize: 26,
    fontWeight: '700',
    marginBottom: 14,
    textAlign: 'center',
  },
  successMessage: {
    color: '#8A8FA8',
    fontSize: 15,
    lineHeight: 24,
    textAlign: 'center',
    marginBottom: 16,
  },
  successEmail: {
    color: '#7C83FD',
    fontWeight: '600',
  },
  successHint: {
    color: '#5A5F78',
    fontSize: 13,
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 32,
    paddingHorizontal: 16,
  },
  resendButton: {
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 12,
    borderWidth: 1.5,
    borderColor: '#7C83FD',
  },
  resendButtonText: {
    color: '#7C83FD',
    fontSize: 15,
    fontWeight: '600',
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingTop: 24,
    paddingBottom: 8,
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
