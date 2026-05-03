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
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { AuthStackParamList } from '../../navigation/types';
import { useAuthStore } from '../../store/authStore';
import type { RegisterData } from '../../api/types';
import { useTranslation } from '../../i18n';
import { useTheme } from '../../theme';

type RegisterNavigationProp = NativeStackNavigationProp<AuthStackParamList, 'Register'>;

type Gender = 'male' | 'female' | 'other';

interface FieldErrors {
  firstName?: string;
  lastName?: string;
  username?: string;
  email?: string;
  password?: string;
  confirmPassword?: string;
}

function getPasswordStrengthScore(password: string): number {
  if (password.length === 0) return 0;
  let score = 0;
  if (password.length >= 8) score++;
  if (password.length >= 12) score++;
  if (/[A-Z]/.test(password)) score++;
  if (/[0-9]/.test(password)) score++;
  if (/[^A-Za-z0-9]/.test(password)) score++;
  if (score <= 1) return 1;
  if (score === 2) return 2;
  if (score === 3) return 3;
  return 4;
}

export function RegisterScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const navigation = useNavigation<RegisterNavigationProp>();

  const isLoading = useAuthStore((state) => state.isLoading);
  const storeError = useAuthStore((state) => state.error);
  const register = useAuthStore((state) => state.register);
  const clearError = useAuthStore((state) => state.clearError);

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [gender, setGender] = useState<Gender>('male');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});

  const errorOpacity = useRef(new Animated.Value(0)).current;

  const lastNameRef = useRef<TextInput>(null);
  const usernameRef = useRef<TextInput>(null);
  const emailRef = useRef<TextInput>(null);
  const passwordRef = useRef<TextInput>(null);
  const confirmPasswordRef = useRef<TextInput>(null);

  useEffect(() => {
    return () => {
      clearError();
    };
  }, [clearError]);

  useEffect(() => {
    if (storeError) {
      Animated.timing(errorOpacity, {
        toValue: 1,
        duration: 300,
        useNativeDriver: true,
      }).start();
    } else {
      errorOpacity.setValue(0);
    }
  }, [storeError, errorOpacity]);

  const passwordScore = getPasswordStrengthScore(password);

  const getStrengthLabel = (score: number): string => {
    if (score === 0) return '';
    if (score === 1) return t('password_strength_weak');
    if (score === 2) return t('password_strength_fair');
    if (score === 3) return t('password_strength_good');
    return t('password_strength_strong');
  };

  const getStrengthColor = (score: number): string => {
    if (score === 0) return theme.divider;
    if (score === 1) return '#FF5C5C';
    if (score === 2) return '#FFAA33';
    if (score === 3) return theme.primary;
    return theme.success;
  };

  const strengthLabel = getStrengthLabel(passwordScore);
  const strengthColor = getStrengthColor(passwordScore);

  const validateEmail = (value: string): boolean => {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  };

  const validate = (): boolean => {
    const errors: FieldErrors = {};

    if (!firstName.trim()) errors.firstName = t('error_required_field');
    if (!lastName.trim()) errors.lastName = t('error_required_field');
    if (!username.trim()) {
      errors.username = t('error_required_field');
    } else if (username.trim().length < 3) {
      errors.username = t('error_username_short');
    } else if (!/^[a-zA-Z0-9_]+$/.test(username.trim())) {
      errors.username = t('error_required_field');
    }
    if (!email.trim()) {
      errors.email = t('error_required_field');
    } else if (!validateEmail(email.trim())) {
      errors.email = t('error_email_invalid');
    }
    if (!password) {
      errors.password = t('error_required_field');
    } else if (password.length < 8) {
      errors.password = t('password_min_8');
    }
    if (!confirmPassword) {
      errors.confirmPassword = t('error_required_field');
    } else if (password !== confirmPassword) {
      errors.confirmPassword = t('passwords_mismatch');
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleRegister = async () => {
    clearError();
    if (!validate()) return;

    const data: RegisterData = {
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      username: username.trim().toLowerCase(),
      email: email.trim().toLowerCase(),
      password,
      gender,
    };

    try {
      await register(data);
    } catch {
      // Error is set in the store
    }
  };

  const clearFieldError = (field: keyof FieldErrors) => {
    if (fieldErrors[field]) {
      setFieldErrors((prev) => ({ ...prev, [field]: undefined }));
    }
    if (storeError) clearError();
  };

  const genderLabel = (g: Gender): string => {
    if (g === 'male') return t('gender_male');
    if (g === 'female') return t('gender_female');
    return t('gender_other');
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
          <View style={styles.header}>
            <Text style={[styles.title, { color: theme.text }]}>{t('create_account_title')}</Text>
            <Text style={[styles.subtitle, { color: theme.textTertiary }]}>{t('join_worldmates')}</Text>
          </View>

          {storeError ? (
            <Animated.View style={[styles.errorBanner, { opacity: errorOpacity }]}>
              <Text style={styles.errorBannerText}>{storeError}</Text>
            </Animated.View>
          ) : null}

          <View style={styles.nameRow}>
            <View style={[styles.inputWrapper, styles.nameField]}>
              <Text style={[styles.inputLabel, { color: theme.textTertiary }]}>{t('first_name')}</Text>
              <TextInput
                style={[
                  styles.input,
                  { backgroundColor: theme.inputBackground, color: theme.text },
                  fieldErrors.firstName ? styles.inputError : null,
                ]}
                placeholder={t('first_name')}
                placeholderTextColor={theme.textTertiary}
                value={firstName}
                onChangeText={(text) => {
                  setFirstName(text);
                  clearFieldError('firstName');
                }}
                autoCapitalize="words"
                autoCorrect={false}
                returnKeyType="next"
                onSubmitEditing={() => lastNameRef.current?.focus()}
                selectionColor={theme.primary}
              />
              {fieldErrors.firstName ? (
                <Text style={styles.fieldError}>{fieldErrors.firstName}</Text>
              ) : null}
            </View>

            <View style={[styles.inputWrapper, styles.nameField]}>
              <Text style={[styles.inputLabel, { color: theme.textTertiary }]}>{t('last_name')}</Text>
              <TextInput
                ref={lastNameRef}
                style={[
                  styles.input,
                  { backgroundColor: theme.inputBackground, color: theme.text },
                  fieldErrors.lastName ? styles.inputError : null,
                ]}
                placeholder={t('last_name')}
                placeholderTextColor={theme.textTertiary}
                value={lastName}
                onChangeText={(text) => {
                  setLastName(text);
                  clearFieldError('lastName');
                }}
                autoCapitalize="words"
                autoCorrect={false}
                returnKeyType="next"
                onSubmitEditing={() => usernameRef.current?.focus()}
                selectionColor={theme.primary}
              />
              {fieldErrors.lastName ? (
                <Text style={styles.fieldError}>{fieldErrors.lastName}</Text>
              ) : null}
            </View>
          </View>

          <View style={styles.inputWrapper}>
            <Text style={[styles.inputLabel, { color: theme.textTertiary }]}>{t('username')}</Text>
            <TextInput
              ref={usernameRef}
              style={[
                styles.input,
                { backgroundColor: theme.inputBackground, color: theme.text },
                fieldErrors.username ? styles.inputError : null,
              ]}
              placeholder={t('username')}
              placeholderTextColor={theme.textTertiary}
              value={username}
              onChangeText={(text) => {
                setUsername(text);
                clearFieldError('username');
              }}
              autoCapitalize="none"
              autoCorrect={false}
              returnKeyType="next"
              onSubmitEditing={() => emailRef.current?.focus()}
              selectionColor={theme.primary}
            />
            {fieldErrors.username ? (
              <Text style={styles.fieldError}>{fieldErrors.username}</Text>
            ) : null}
          </View>

          <View style={styles.inputWrapper}>
            <Text style={[styles.inputLabel, { color: theme.textTertiary }]}>{t('email')}</Text>
            <TextInput
              ref={emailRef}
              style={[
                styles.input,
                { backgroundColor: theme.inputBackground, color: theme.text },
                fieldErrors.email ? styles.inputError : null,
              ]}
              placeholder={t('email')}
              placeholderTextColor={theme.textTertiary}
              value={email}
              onChangeText={(text) => {
                setEmail(text);
                clearFieldError('email');
              }}
              keyboardType="email-address"
              autoCapitalize="none"
              autoCorrect={false}
              autoComplete="email"
              returnKeyType="next"
              onSubmitEditing={() => passwordRef.current?.focus()}
              selectionColor={theme.primary}
            />
            {fieldErrors.email ? (
              <Text style={styles.fieldError}>{fieldErrors.email}</Text>
            ) : null}
          </View>

          <View style={styles.inputWrapper}>
            <Text style={[styles.inputLabel, { color: theme.textTertiary }]}>{t('password')}</Text>
            <View
              style={[
                styles.passwordContainer,
                { backgroundColor: theme.inputBackground },
                fieldErrors.password ? styles.passwordContainerError : null,
              ]}
            >
              <TextInput
                ref={passwordRef}
                style={[styles.passwordInput, { color: theme.text }]}
                placeholder={t('password')}
                placeholderTextColor={theme.textTertiary}
                value={password}
                onChangeText={(text) => {
                  setPassword(text);
                  clearFieldError('password');
                }}
                secureTextEntry={!showPassword}
                autoCapitalize="none"
                autoCorrect={false}
                returnKeyType="next"
                onSubmitEditing={() => confirmPasswordRef.current?.focus()}
                selectionColor={theme.primary}
              />
              <TouchableOpacity
                style={styles.eyeButton}
                onPress={() => setShowPassword((v) => !v)}
                hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              >
                <Text style={styles.eyeIcon}>{showPassword ? '🙈' : '👁️'}</Text>
              </TouchableOpacity>
            </View>
            {password.length > 0 && (
              <View style={styles.strengthContainer}>
                <View style={styles.strengthBars}>
                  {[1, 2, 3, 4].map((bar) => (
                    <View
                      key={bar}
                      style={[
                        styles.strengthBar,
                        {
                          backgroundColor:
                            bar <= passwordScore
                              ? strengthColor
                              : theme.divider,
                        },
                      ]}
                    />
                  ))}
                </View>
                {strengthLabel ? (
                  <Text style={[styles.strengthLabel, { color: strengthColor }]}>
                    {strengthLabel}
                  </Text>
                ) : null}
              </View>
            )}
            {fieldErrors.password ? (
              <Text style={styles.fieldError}>{fieldErrors.password}</Text>
            ) : null}
          </View>

          <View style={styles.inputWrapper}>
            <Text style={[styles.inputLabel, { color: theme.textTertiary }]}>{t('confirm_password')}</Text>
            <View
              style={[
                styles.passwordContainer,
                { backgroundColor: theme.inputBackground },
                fieldErrors.confirmPassword ? styles.passwordContainerError : null,
              ]}
            >
              <TextInput
                ref={confirmPasswordRef}
                style={[styles.passwordInput, { color: theme.text }]}
                placeholder={t('confirm_password')}
                placeholderTextColor={theme.textTertiary}
                value={confirmPassword}
                onChangeText={(text) => {
                  setConfirmPassword(text);
                  clearFieldError('confirmPassword');
                }}
                secureTextEntry={!showConfirmPassword}
                autoCapitalize="none"
                autoCorrect={false}
                returnKeyType="done"
                onSubmitEditing={handleRegister}
                selectionColor={theme.primary}
              />
              <TouchableOpacity
                style={styles.eyeButton}
                onPress={() => setShowConfirmPassword((v) => !v)}
                hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              >
                <Text style={styles.eyeIcon}>{showConfirmPassword ? '🙈' : '👁️'}</Text>
              </TouchableOpacity>
            </View>
            {fieldErrors.confirmPassword ? (
              <Text style={styles.fieldError}>{fieldErrors.confirmPassword}</Text>
            ) : null}
          </View>

          <View style={styles.inputWrapper}>
            <Text style={[styles.inputLabel, { color: theme.textTertiary }]}>{t('gender')}</Text>
            <View style={styles.genderRow}>
              {(['male', 'female', 'other'] as Gender[]).map((g) => (
                <TouchableOpacity
                  key={g}
                  style={[
                    styles.genderOption,
                    { backgroundColor: theme.inputBackground, borderColor: theme.divider },
                    gender === g && { backgroundColor: theme.surfaceElevated, borderColor: theme.primary },
                  ]}
                  onPress={() => setGender(g)}
                  activeOpacity={0.7}
                >
                  <Text style={[styles.genderText, { color: theme.textTertiary }, gender === g && { color: theme.primary }]}>
                    {genderLabel(g)}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>

          <TouchableOpacity
            style={[
              styles.createButton,
              { backgroundColor: theme.primary, shadowColor: theme.primary },
              isLoading && styles.createButtonDisabled,
            ]}
            onPress={handleRegister}
            activeOpacity={0.85}
            disabled={isLoading}
          >
            {isLoading ? (
              <ActivityIndicator color={theme.white} size="small" />
            ) : (
              <Text style={[styles.createButtonText, { color: theme.white }]}>{t('sign_up')}</Text>
            )}
          </TouchableOpacity>

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
  header: {
    paddingTop: 40,
    paddingBottom: 28,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    marginBottom: 6,
  },
  subtitle: {
    fontSize: 15,
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
  nameRow: {
    flexDirection: 'row',
    gap: 12,
  },
  nameField: {
    flex: 1,
  },
  inputWrapper: {
    marginBottom: 18,
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
  passwordContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'transparent',
  },
  passwordContainerError: {
    borderColor: '#FF5C5C',
  },
  passwordInput: {
    flex: 1,
    paddingVertical: 14,
    paddingHorizontal: 16,
    fontSize: 16,
  },
  eyeButton: {
    paddingHorizontal: 14,
    paddingVertical: 14,
  },
  eyeIcon: {
    fontSize: 18,
  },
  strengthContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 8,
    gap: 8,
  },
  strengthBars: {
    flexDirection: 'row',
    gap: 4,
  },
  strengthBar: {
    width: 36,
    height: 4,
    borderRadius: 2,
  },
  strengthLabel: {
    fontSize: 12,
    fontWeight: '600',
  },
  fieldError: {
    color: '#FF6B6B',
    fontSize: 12,
    marginTop: 6,
    fontWeight: '500',
  },
  genderRow: {
    flexDirection: 'row',
    gap: 10,
  },
  genderOption: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'center',
  },
  genderText: {
    fontSize: 14,
    fontWeight: '600',
  },
  createButton: {
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: 'center',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 12,
    elevation: 6,
    minHeight: 52,
    justifyContent: 'center',
    marginTop: 8,
  },
  createButtonDisabled: {
    opacity: 0.7,
  },
  createButtonText: {
    fontSize: 17,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 24,
  },
  footerText: {
    fontSize: 15,
  },
  footerLink: {
    fontSize: 15,
    fontWeight: '600',
  },
});
