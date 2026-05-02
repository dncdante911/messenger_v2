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

type VerificationScreenProps = NativeStackScreenProps<AuthStackParamList, 'Verification'>;
type VerificationNavigationProp = NativeStackNavigationProp<AuthStackParamList, 'Verification'>;

const CODE_LENGTH = 6;

export function VerificationScreen() {
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
      setError(`Please enter the full ${CODE_LENGTH}-digit code.`);
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      // Real implementation: await authApi.verifyCode(route.params.userId, verifyCode);
      await new Promise<void>((resolve, reject) => {
        setTimeout(() => {
          if (verifyCode === '000000') {
            reject(new Error('Invalid verification code.'));
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
        err?.response?.data?.message || err?.message || 'Invalid verification code.';
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
      const message = err?.response?.data?.message || 'Failed to resend code. Try again.';
      setError(message);
    } finally {
      setIsResending(false);
      inputRefs.current[0]?.focus();
    }
  };

  const filledCount = code.filter((d) => d !== '').length;

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor="#1A1B2E" />
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
            <Text style={styles.backArrow}>←</Text>
            <Text style={styles.backText}>Back</Text>
          </TouchableOpacity>

          <View style={styles.iconContainer}>
            <View style={styles.iconCircle}>
              <Text style={styles.iconEmoji}>✉️</Text>
            </View>
          </View>

          <Text style={styles.title}>Verify your email</Text>
          <Text style={styles.subtitle}>
            We sent a {CODE_LENGTH}-digit code to{'\n'}
            <Text style={styles.emailHighlight}>{email}</Text>
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
                    code[i] ? styles.codeInputFilled : null,
                    error ? styles.codeInputError : null,
                  ]}
                  value={code[i]}
                  onChangeText={(text) => handleCodeChange(text, i)}
                  onKeyPress={({ nativeEvent }) => handleKeyPress(nativeEvent.key, i)}
                  keyboardType="number-pad"
                  maxLength={1}
                  selectionColor="#7C83FD"
                  autoFocus={i === 0}
                  selectTextOnFocus
                />
              ))}
          </View>

          <TouchableOpacity
            style={[
              styles.verifyButton,
              (isLoading || filledCount < CODE_LENGTH) && styles.verifyButtonDisabled,
            ]}
            onPress={() => handleVerify()}
            activeOpacity={0.85}
            disabled={isLoading || filledCount < CODE_LENGTH}
          >
            {isLoading ? (
              <ActivityIndicator color="#FFFFFF" size="small" />
            ) : (
              <Text style={styles.verifyButtonText}>Verify</Text>
            )}
          </TouchableOpacity>

          <View style={styles.resendContainer}>
            <Text style={styles.resendText}>Didn't receive the code? </Text>
            {canResend ? (
              <TouchableOpacity onPress={handleResend} activeOpacity={0.7} disabled={isResending}>
                {isResending ? (
                  <ActivityIndicator color="#7C83FD" size="small" />
                ) : (
                  <Text style={styles.resendLink}>Resend</Text>
                )}
              </TouchableOpacity>
            ) : (
              <Text style={styles.resendCooldown}>Resend in {resendCooldown}s</Text>
            )}
          </View>

          <Animated.View style={[styles.successOverlay, { opacity: successOpacity }]}>
            <View style={styles.successIconCircle}>
              <Text style={styles.successCheckmark}>✓</Text>
            </View>
            <Text style={styles.successText}>Verified!</Text>
          </Animated.View>
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
    paddingTop: 28,
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
  title: {
    color: '#FFFFFF',
    fontSize: 26,
    fontWeight: '700',
    textAlign: 'center',
    marginBottom: 12,
  },
  subtitle: {
    color: '#8A8FA8',
    fontSize: 15,
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 28,
  },
  emailHighlight: {
    color: '#7C83FD',
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
    backgroundColor: '#2A2B3D',
    borderWidth: 1.5,
    borderColor: '#3A3B52',
    color: '#FFFFFF',
    fontSize: 22,
    fontWeight: '700',
    textAlign: 'center',
  },
  codeInputFilled: {
    borderColor: '#7C83FD',
    backgroundColor: '#2E2F4A',
  },
  codeInputError: {
    borderColor: '#FF5C5C',
  },
  verifyButton: {
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
    marginBottom: 20,
  },
  verifyButtonDisabled: {
    opacity: 0.5,
  },
  verifyButtonText: {
    color: '#FFFFFF',
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
    color: '#8A8FA8',
    fontSize: 14,
  },
  resendLink: {
    color: '#7C83FD',
    fontSize: 14,
    fontWeight: '600',
  },
  resendCooldown: {
    color: '#5A5F78',
    fontSize: 14,
    fontWeight: '500',
  },
  successOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#1A1B2E',
    alignItems: 'center',
    justifyContent: 'center',
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
    marginBottom: 20,
    shadowColor: '#4CAF82',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.3,
    shadowRadius: 16,
    elevation: 8,
  },
  successCheckmark: {
    color: '#4CAF82',
    fontSize: 48,
    fontWeight: '700',
  },
  successText: {
    color: '#FFFFFF',
    fontSize: 26,
    fontWeight: '700',
  },
});
