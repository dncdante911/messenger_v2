import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import type { AuthStackParamList } from './types';
import { SplashScreen } from '../screens/auth/SplashScreen';
import { LanguageSelectionScreen } from '../screens/auth/LanguageSelectionScreen';
import { LoginScreen } from '../screens/auth/LoginScreen';
import { RegisterScreen } from '../screens/auth/RegisterScreen';
import { ForgotPasswordScreen } from '../screens/auth/ForgotPasswordScreen';
import { VerificationScreen } from '../screens/auth/VerificationScreen';

const AuthStack = createNativeStackNavigator<AuthStackParamList>();

export function AuthNavigator() {
  return (
    <AuthStack.Navigator
      initialRouteName="Splash"
      screenOptions={{
        headerShown: false,
        animation: 'slide_from_right',
      }}
    >
      <AuthStack.Screen
        name="Splash"
        component={SplashScreen}
        options={{ animation: 'none' }}
      />
      <AuthStack.Screen
        name="LanguageSelection"
        component={LanguageSelectionScreen}
      />
      <AuthStack.Screen
        name="Login"
        component={LoginScreen}
      />
      <AuthStack.Screen
        name="Register"
        component={RegisterScreen}
      />
      <AuthStack.Screen
        name="ForgotPassword"
        component={ForgotPasswordScreen}
      />
      <AuthStack.Screen
        name="Verification"
        component={VerificationScreen}
      />
    </AuthStack.Navigator>
  );
}
