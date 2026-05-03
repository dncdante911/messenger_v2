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
  Platform,
  ActivityIndicator,
  Animated,
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp, NativeStackScreenProps } from '@react-navigation/native-stack';
import type { AuthStackParamList } from '../../navigation/types';
import { useTranslation } from '../../i18n';
import { useTheme } from '../../theme';

type VerificationScreenProps = NativeStackScreenProps<AuthStackParamList, 'Verification'>;
type VerificationNavigationProp = NativeStackNavigationProp<AuthStackParamList, 'Verification'>;

const CODE_LENGTH = 6;

export function VerificationScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const navigation = useNavigation<VerificationNavigationProp>();
  const route = useRoute<VerificationScreenProps['route']>();
  const { email } = route.params;

  const [code, setCode] = useState<string[]>(Array(CODE_LENGTH).fill(''));
  const [isLoading, setIsLoading] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resendCooldown, setResendCooldown] = useState(60);
  const [canResend, setCanResend] = useState(false);

  const inputRefs = useRef<Array<TextInput | null>>(Array(CODE_LENGTH).fill(null));
  const errorOpacity = useRef(new Animated.Value(0)).current;
  const successOpacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const interval = setInterval(() => {
      setResendCooldown((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          setCanResend(true);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (error) {
      Animated.timing(errorOpacity, {
        toValue: 1,
        duration: 300,
        useNativeDriver: true,
      }).start();
    } else {
      errorOpacity.setValue(0);
    }
  }, [error, errorOpacity]);

  const handleCodeChange = (text: string, index: number) => {
    const digit = text.replace(/[^0-9]/g, '').slice(-1);
    const newCode = [...code];
    newCode[index] = digit;
    setCode(newCode);
    if (error) setError(null);

    if (digit && index < CODE_LENGTH - 1) {
      inputRefs.current[index + 1]?.focus();
    }

    if (newCode.every((d) => d !== '') && digit) {
      handleVerify(newCode.join(''));
    }
  };

  const handleKeyPress = (key: string, index: number) => {
    if (key === 'Backspace' && !code[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
      const newCode = [...code];
      newCode[index - 1] = '';
      setCode(newCode);
    }
  };

  const handleVerify = async (codeString?: string) => {
    const verifyCode = codeString ?? code.join('');
    if (verifyCode.length < CODE_LENGTH) {
      setError(t('invalid_code'));
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      // Real implementation: await authApi.verifyCode(route.params.userId, verifyCode);
      await new Promise<void>((resolve, reject) => {
        setTimeout(() => {
          if (verifyCode === '000000') {
            reject(new Error(t('invalid_code')));
          } else {
            resolve();
          }
        }, 1000);
      });

      setIsLoading(false);
      Animated.timing(successOpacity, {
        toValue: 1,
        duration: 400,
        useNativeDriver: true,
      }).start(() => {
        setTimeout(() => {
          navigation.replace('Login');
        }, 1200);
      });
    } catch (err: any) {
      setIsLoading(false);
      const message =
        err?.response?.data?.message || err?.message || t('invalid_code');
      setError(message);
      setCode(Array(CODE_LENGTH).fill(''));
      inputRefs.current[0]?.focus();
    }
  };

  const handleResend = async () => {
    if (!canResend || isResending) return;
    setIsResending(true);
    setError(null);
    setCode(Array(CODE_LENGTH).fill(''));

    try {
      // Real implementation: await authApi.resendVerificationCode(route.params.userId);
      await new Promise<void>((resolve) => setTimeout(resolve, 800));
      setCanResend(false);
      setResendCooldown(60);

      const interval = setInterval(() => {
        setResendCooldown((prev) => {
          if (prev <= 1) {
            clearInterval(interval);
            setCanResend(true);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } catch (err: any) {
      const message = err?.response?.data?.message || t('error_network');
      setError(message);
    } finally {
      setIsResending(false);
      inputRefs.current[0]?.focus();
    }
  };

  const filledCount = code.filter((d) => d !== '').length;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor: theme.background }]}>
      <StatusBar barStyle="light-content" backgroundColor={theme.background} />
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <View style={styles.container}>
          <TouchableOpacity
            style={styles.backButton}
            onPress={() => navigation.goBack()}
            hitSlop={{ top: 12, bottom: 12, left: 12, right: 12 }}
            activeOpacity={0.7}
          >
            <Text style={[styles.backArrow, { color: theme.primary }]}>←</Text>
            <Text style={[styles.backText, { color: theme.primary }]}>{t('back')}</Text>
          </TouchableOpacity>

          <View style={styles.iconContainer}>
            <View style={[styles.iconCircle, { backgroundColor: theme.inputBackground, borderColor: theme.primary, shadowColor: theme.primary }]}>
              <Text style={styles.iconEmoji}>✉️</Text>
            </View>
          </View>

          <Text style={[styles.title, { color: theme.text }]}>{t('verification_code')}</Text>
          <Text style={[styles.subtitle, { color: theme.textTertiary }]}>
            {t('enter_verification_code')}{'\n'}
            <Text style={[styles.emailHighlight, { color: theme.primary }]}>{email}</Text>
          </Text>

          {error ? (
            <Animated.View style={[styles.errorBanner, { opacity: errorOpacity }]}>
              <Text style={styles.errorBannerText}>{error}</Text>
            </Animated.View>
          ) : null}

          <View style={styles.codeRow}>
            {Array(CODE_LENGTH)
              .fill(null)
              .map((_, i) => (
                <TextInput
                  key={i}
                  ref={(ref) => {
                    inputRefs.current[i] = ref;
                  }}
                  style={[
                    styles.codeInput,
                    { backgroundColor: theme.inputBackground, color: theme.text },
                    code[i] ? { borderColor: theme.primary, backgroundColor: theme.surfaceElevated } : null,
                    error ? styles.codeInputError : null,
                  ]}
                  value={code[i]}
                  onChangeText={(text) => handleCodeChange(text, i)}
                  onKeyPress={({ nativeEvent }) => handleKeyPress(nativeEvent.key, i)}
                  keyboardType="number-pad"
                  maxLength={1}
                  selectionColor={theme.primary}
                  autoFocus={i === 0}
                  selectTextOnFocus
                />
              ))}
          </View>

          <TouchableOpacity
            style={[
              styles.verifyButton,
              { backgroundColor: theme.primary, shadowColor: theme.primary },
              (isLoading || filledCount < CODE_LENGTH) && styles.verifyButtonDisabled,
            ]}
            onPress={() => handleVerify()}
            activeOpacity={0.85}
            disabled={isLoading || filledCount < CODE_LENGTH}
          >
            {isLoading ? (
              <ActivityIndicator color={theme.white} size="small" />
            ) : (
              <Text style={[styles.verifyButtonText, { color: theme.white }]}>{t('verify')}</Text>
            )}
          </TouchableOpacity>

          <View style={styles.resendContainer}>
            <Text style={[styles.resendText, { color: theme.textTertiary }]}>{t('resend_code')} </Text>
            {canResend ? (
              <TouchableOpacity onPress={handleResend} activeOpacity={0.7} disabled={isResending}>
                {isResending ? (
                  <ActivityIndicator color={theme.primary} size="small" />
                ) : (
                  <Text style={[styles.resendLink, { color: theme.primary }]}>{t('resend_code')}</Text>
                )}
              </TouchableOpacity>
            ) : (
              <Text style={[styles.resendCooldown, { color: theme.textTertiary }]}>
                {t('resend_in')} {resendCooldown}{t('seconds')}
              </Text>
            )}
          </View>

          <Animated.View style={[styles.successOverlay, { opacity: successOpacity, backgroundColor: theme.background }]}>
            <View style={[styles.successIconCircle, { backgroundColor: 'rgba(76, 175, 130, 0.15)', borderColor: theme.success }]}>
              <Text style={[styles.successCheckmark, { color: theme.success }]}>✓</Text>
            </View>
            <Text style={[styles.successText, { color: theme.text }]}>{t('login_success')}</Text>
          </Animated.View>
        </View>
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
  container: {
    flex: 1,
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
    paddingTop: 28,
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
  title: {
    fontSize: 26,
    fontWeight: '700',
    textAlign: 'center',
    marginBottom: 12,
  },
  subtitle: {
    fontSize: 15,
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 28,
  },
  emailHighlight: {
    fontWeight: '600',
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
  codeRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 10,
    marginBottom: 32,
  },
  codeInput: {
    width: 48,
    height: 56,
    borderRadius: 12,
    borderWidth: 1.5,
    borderColor: 'transparent',
    fontSize: 22,
    fontWeight: '700',
    textAlign: 'center',
  },
  codeInputError: {
    borderColor: '#FF5C5C',
  },
  verifyButton: {
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: 'center',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 12,
    elevation: 6,
    minHeight: 52,
    justifyContent: 'center',
    marginBottom: 20,
  },
  verifyButtonDisabled: {
    opacity: 0.5,
  },
  verifyButtonText: {
    fontSize: 17,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  resendContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  },
  resendText: {
    fontSize: 14,
  },
  resendLink: {
    fontSize: 14,
    fontWeight: '600',
  },
  resendCooldown: {
    fontSize: 14,
    fontWeight: '500',
  },
  successOverlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
  },
  successIconCircle: {
    width: 100,
    height: 100,
    borderRadius: 50,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 20,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.3,
    shadowRadius: 16,
    elevation: 8,
  },
  successCheckmark: {
    fontSize: 48,
    fontWeight: '700',
  },
  successText: {
    fontSize: 26,
    fontWeight: '700',
  },
});
