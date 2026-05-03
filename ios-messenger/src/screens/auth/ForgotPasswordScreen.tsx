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
import { useTranslation } from '../../i18n';
import { useTheme } from '../../theme';

type ForgotPasswordNavigationProp = NativeStackNavigationProp<
  AuthStackParamList,
  'ForgotPassword'
>;

export function ForgotPasswordScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
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
      setError(t('error_required_field'));
      Animated.timing(errorOpacity, {
        toValue: 1,
        duration: 300,
        useNativeDriver: true,
      }).start();
      return;
    }

    if (!validateEmail(email.trim())) {
      setError(t('error_email_invalid'));
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
      await new Promise<void>((resolve, _reject) => {
        setTimeout(() => {
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
        t('error_network');
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
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]}>
      <StatusBar barStyle="light-content" backgroundColor={theme.background} />
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
            <Text style={[styles.backArrow, { color: theme.primary }]}>←</Text>
            <Text style={[styles.backText, { color: theme.primary }]}>{t('back')}</Text>
          </TouchableOpacity>

          {!isSuccess ? (
            <>
              <View style={styles.iconContainer}>
                <View style={[styles.iconCircle, { backgroundColor: theme.inputBackground, borderColor: theme.primary, shadowColor: theme.primary }]}>
                  <Text style={styles.iconEmoji}>🔒</Text>
                </View>
              </View>

              <View style={styles.header}>
                <Text style={[styles.title, { color: theme.text }]}>{t('forgot_password')}</Text>
                <Text style={[styles.subtitle, { color: theme.textTertiary }]}>
                  {t('check_email_desc')}
                </Text>
              </View>

              {error ? (
                <Animated.View style={[styles.errorBanner, { opacity: errorOpacity }]}>
                  <Text style={styles.errorBannerText}>{error}</Text>
                </Animated.View>
              ) : null}

              <View style={styles.inputWrapper}>
                <Text style={[styles.inputLabel, { color: theme.textTertiary }]}>{t('email')}</Text>
                <TextInput
                  style={[
                    styles.input,
                    { backgroundColor: theme.inputBackground, color: theme.text },
                    error ? styles.inputError : null,
                  ]}
                  placeholder={t('email')}
                  placeholderTextColor={theme.textTertiary}
                  value={email}
                  onChangeText={handleEmailChange}
                  keyboardType="email-address"
                  autoCapitalize="none"
                  autoCorrect={false}
                  autoComplete="email"
                  returnKeyType="done"
                  onSubmitEditing={handleSend}
                  selectionColor={theme.primary}
                  autoFocus
                />
              </View>

              <TouchableOpacity
                style={[
                  styles.sendButton,
                  { backgroundColor: theme.primary, shadowColor: theme.primary },
                  isLoading && styles.sendButtonDisabled,
                ]}
                onPress={handleSend}
                activeOpacity={0.85}
                disabled={isLoading}
              >
                {isLoading ? (
                  <ActivityIndicator color={theme.white} size="small" />
                ) : (
                  <Text style={[styles.sendButtonText, { color: theme.white }]}>{t('send_reset_link')}</Text>
                )}
              </TouchableOpacity>
            </>
          ) : (
            <Animated.View style={[styles.successContainer, { opacity: successOpacity }]}>
              <View style={[styles.successIconCircle, { backgroundColor: 'rgba(76, 175, 130, 0.15)', borderColor: theme.success }]}>
                <Text style={[styles.successCheckmark, { color: theme.success }]}>✓</Text>
              </View>
              <Text style={[styles.successTitle, { color: theme.text }]}>{t('check_email')}</Text>
              <Text style={[styles.successMessage, { color: theme.textTertiary }]}>
                {t('check_email_desc')}{'\n'}
                <Text style={[styles.successEmail, { color: theme.primary }]}>{email.trim().toLowerCase()}</Text>
              </Text>
              <Text style={[styles.successHint, { color: theme.textTertiary }]}>
                {t('check_email_desc')}
              </Text>

              <TouchableOpacity
                style={[styles.resendButton, { borderColor: theme.primary }]}
                onPress={() => {
                  setIsSuccess(false);
                  successOpacity.setValue(0);
                  setEmail('');
                }}
                activeOpacity={0.7}
              >
                <Text style={[styles.resendButtonText, { color: theme.primary }]}>{t('try_different_email')}</Text>
              </TouchableOpacity>
            </Animated.View>
          )}

          <View style={styles.footer}>
            <Text style={[styles.footerText, { color: theme.textTertiary }]}>{t('already_have_account')} </Text>
            <TouchableOpacity
              onPress={() => navigation.navigate('Login')}
              activeOpacity={0.7}
            >
              <Text style={[styles.footerLink, { color: theme.primary }]}>{t('sign_in')}</Text>
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
    fontSize: 20,
    fontWeight: '500',
  },
  backText: {
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
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
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
    fontSize: 26,
    fontWeight: '700',
    marginBottom: 10,
  },
  subtitle: {
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
    fontSize: 13,
    fontWeight: '600',
    marginBottom: 8,
    letterSpacing: 0.3,
  },
  input: {
    borderRadius: 12,
    paddingVertical: 14,
    paddingHorizontal: 16,
    fontSize: 16,
    borderWidth: 1,
    borderColor: 'transparent',
  },
  inputError: {
    borderColor: '#FF5C5C',
  },
  sendButton: {
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: 'center',
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
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 28,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.3,
    shadowRadius: 16,
    elevation: 8,
  },
  successCheckmark: {
    fontSize: 44,
    fontWeight: '700',
  },
  successTitle: {
    fontSize: 26,
    fontWeight: '700',
    marginBottom: 14,
    textAlign: 'center',
  },
  successMessage: {
    fontSize: 15,
    lineHeight: 24,
    textAlign: 'center',
    marginBottom: 16,
  },
  successEmail: {
    fontWeight: '600',
  },
  successHint: {
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
  },
  resendButtonText: {
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
    fontSize: 15,
  },
  footerLink: {
    fontSize: 15,
    fontWeight: '600',
  },
});
