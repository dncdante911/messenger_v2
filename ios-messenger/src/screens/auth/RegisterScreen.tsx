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

function getPasswordStrength(password: string): { score: number; label: string; color: string } {
  if (password.length === 0) return { score: 0, label: '', color: '#3A3B52' };
  let score = 0;
  if (password.length >= 8) score++;
  if (password.length >= 12) score++;
  if (/[A-Z]/.test(password)) score++;
  if (/[0-9]/.test(password)) score++;
  if (/[^A-Za-z0-9]/.test(password)) score++;

  if (score <= 1) return { score: 1, label: 'Weak', color: '#FF5C5C' };
  if (score === 2) return { score: 2, label: 'Fair', color: '#FFAA33' };
  if (score === 3) return { score: 3, label: 'Good', color: '#7C83FD' };
  return { score: 4, label: 'Strong', color: '#4CAF82' };
}

export function RegisterScreen() {
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

  const passwordStrength = getPasswordStrength(password);

  const validateEmail = (value: string): boolean => {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  };

  const validate = (): boolean => {
    const errors: FieldErrors = {};

    if (!firstName.trim()) errors.firstName = 'First name is required.';
    if (!lastName.trim()) errors.lastName = 'Last name is required.';
    if (!username.trim()) {
      errors.username = 'Username is required.';
    } else if (username.trim().length < 3) {
      errors.username = 'Username must be at least 3 characters.';
    } else if (!/^[a-zA-Z0-9_]+$/.test(username.trim())) {
      errors.username = 'Username can only contain letters, numbers, and underscores.';
    }
    if (!email.trim()) {
      errors.email = 'Email is required.';
    } else if (!validateEmail(email.trim())) {
      errors.email = 'Please enter a valid email address.';
    }
    if (!password) {
      errors.password = 'Password is required.';
    } else if (password.length < 8) {
      errors.password = 'Password must be at least 8 characters.';
    }
    if (!confirmPassword) {
      errors.confirmPassword = 'Please confirm your password.';
    } else if (password !== confirmPassword) {
      errors.confirmPassword = 'Passwords do not match.';
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
          <View style={styles.header}>
            <Text style={styles.title}>Create Account</Text>
            <Text style={styles.subtitle}>Join WorldMates today</Text>
          </View>

          {storeError ? (
            <Animated.View style={[styles.errorBanner, { opacity: errorOpacity }]}>
              <Text style={styles.errorBannerText}>{storeError}</Text>
            </Animated.View>
          ) : null}

          <View style={styles.nameRow}>
            <View style={[styles.inputWrapper, styles.nameField]}>
              <Text style={styles.inputLabel}>First Name</Text>
              <TextInput
                style={[styles.input, fieldErrors.firstName ? styles.inputError : null]}
                placeholder="First name"
                placeholderTextColor="#4A4E6A"
                value={firstName}
                onChangeText={(text) => {
                  setFirstName(text);
                  clearFieldError('firstName');
                }}
                autoCapitalize="words"
                autoCorrect={false}
                returnKeyType="next"
                onSubmitEditing={() => lastNameRef.current?.focus()}
                selectionColor="#7C83FD"
              />
              {fieldErrors.firstName ? (
                <Text style={styles.fieldError}>{fieldErrors.firstName}</Text>
              ) : null}
            </View>

            <View style={[styles.inputWrapper, styles.nameField]}>
              <Text style={styles.inputLabel}>Last Name</Text>
              <TextInput
                ref={lastNameRef}
                style={[styles.input, fieldErrors.lastName ? styles.inputError : null]}
                placeholder="Last name"
                placeholderTextColor="#4A4E6A"
                value={lastName}
                onChangeText={(text) => {
                  setLastName(text);
                  clearFieldError('lastName');
                }}
                autoCapitalize="words"
                autoCorrect={false}
                returnKeyType="next"
                onSubmitEditing={() => usernameRef.current?.focus()}
                selectionColor="#7C83FD"
              />
              {fieldErrors.lastName ? (
                <Text style={styles.fieldError}>{fieldErrors.lastName}</Text>
              ) : null}
            </View>
          </View>

          <View style={styles.inputWrapper}>
            <Text style={styles.inputLabel}>Username</Text>
            <TextInput
              ref={usernameRef}
              style={[styles.input, fieldErrors.username ? styles.inputError : null]}
              placeholder="Choose a username"
              placeholderTextColor="#4A4E6A"
              value={username}
              onChangeText={(text) => {
                setUsername(text);
                clearFieldError('username');
              }}
              autoCapitalize="none"
              autoCorrect={false}
              returnKeyType="next"
              onSubmitEditing={() => emailRef.current?.focus()}
              selectionColor="#7C83FD"
            />
            {fieldErrors.username ? (
              <Text style={styles.fieldError}>{fieldErrors.username}</Text>
            ) : null}
          </View>

          <View style={styles.inputWrapper}>
            <Text style={styles.inputLabel}>Email</Text>
            <TextInput
              ref={emailRef}
              style={[styles.input, fieldErrors.email ? styles.inputError : null]}
              placeholder="Enter your email"
              placeholderTextColor="#4A4E6A"
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
              selectionColor="#7C83FD"
            />
            {fieldErrors.email ? (
              <Text style={styles.fieldError}>{fieldErrors.email}</Text>
            ) : null}
          </View>

          <View style={styles.inputWrapper}>
            <Text style={styles.inputLabel}>Password</Text>
            <View
              style={[
                styles.passwordContainer,
                fieldErrors.password ? styles.passwordContainerError : null,
              ]}
            >
              <TextInput
                ref={passwordRef}
                style={styles.passwordInput}
                placeholder="Create a password"
                placeholderTextColor="#4A4E6A"
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
                            bar <= passwordStrength.score
                              ? passwordStrength.color
                              : '#3A3B52',
                        },
                      ]}
                    />
                  ))}
                </View>
                {passwordStrength.label ? (
                  <Text style={[styles.strengthLabel, { color: passwordStrength.color }]}>
                    {passwordStrength.label}
                  </Text>
                ) : null}
              </View>
            )}
            {fieldErrors.password ? (
              <Text style={styles.fieldError}>{fieldErrors.password}</Text>
            ) : null}
          </View>

          <View style={styles.inputWrapper}>
            <Text style={styles.inputLabel}>Confirm Password</Text>
            <View
              style={[
                styles.passwordContainer,
                fieldErrors.confirmPassword ? styles.passwordContainerError : null,
              ]}
            >
              <TextInput
                ref={confirmPasswordRef}
                style={styles.passwordInput}
                placeholder="Confirm your password"
                placeholderTextColor="#4A4E6A"
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
                selectionColor="#7C83FD"
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
            <Text style={styles.inputLabel}>Gender</Text>
            <View style={styles.genderRow}>
              {(['male', 'female', 'other'] as Gender[]).map((g) => (
                <TouchableOpacity
                  key={g}
                  style={[styles.genderOption, gender === g && styles.genderOptionSelected]}
                  onPress={() => setGender(g)}
                  activeOpacity={0.7}
                >
                  <Text style={[styles.genderText, gender === g && styles.genderTextSelected]}>
                    {g.charAt(0).toUpperCase() + g.slice(1)}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>

          <TouchableOpacity
            style={[styles.createButton, isLoading && styles.createButtonDisabled]}
            onPress={handleRegister}
            activeOpacity={0.85}
            disabled={isLoading}
          >
            {isLoading ? (
              <ActivityIndicator color="#FFFFFF" size="small" />
            ) : (
              <Text style={styles.createButtonText}>Create Account</Text>
            )}
          </TouchableOpacity>

          <View style={styles.footer}>
            <Text style={styles.footerText}>Already have an account? </Text>
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
  header: {
    paddingTop: 40,
    paddingBottom: 28,
  },
  title: {
    color: '#FFFFFF',
    fontSize: 28,
    fontWeight: '700',
    marginBottom: 6,
  },
  subtitle: {
    color: '#8A8FA8',
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
  passwordContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#2A2B3D',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#3A3B52',
  },
  passwordContainerError: {
    borderColor: '#FF5C5C',
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
    backgroundColor: '#2A2B3D',
    borderWidth: 1,
    borderColor: '#3A3B52',
    alignItems: 'center',
  },
  genderOptionSelected: {
    backgroundColor: '#2E2F4A',
    borderColor: '#7C83FD',
  },
  genderText: {
    color: '#8A8FA8',
    fontSize: 14,
    fontWeight: '600',
  },
  genderTextSelected: {
    color: '#7C83FD',
  },
  createButton: {
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
    marginTop: 8,
  },
  createButtonDisabled: {
    opacity: 0.7,
  },
  createButtonText: {
    color: '#FFFFFF',
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
    color: '#8A8FA8',
    fontSize: 15,
  },
  footerLink: {
    color: '#7C83FD',
    fontSize: 15,
    fontWeight: '600',
  },
});
