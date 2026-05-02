// ============================================================
// WorldMates Messenger — Main (Authenticated) Navigator
//
// Layout:
//   RootStack (headerShown: false)
//     └── BottomTabs
//           ├── Chats      → ChatsScreen
//           ├── Calls      → CallsScreen (placeholder)
//           ├── Stories    → StoriesScreen (placeholder)
//           └── Settings   → SettingsScreen (placeholder)
//     ── Messages          → MessagesScreen (full-screen overlay)
//     ── UserProfile       → placeholder
//     ── GlobalSearch      → placeholder
//     ── SavedMessages     → placeholder
//     ── Notes             → placeholder
// ============================================================

import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { Feather } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ChatsScreen } from '../screens/chats/ChatsScreen';
import { MessagesScreen } from '../screens/messages/MessagesScreen';
import type { MainTabParamList, RootStackParamList } from './types';

// ─────────────────────────────────────────────────────────────
// PLACEHOLDER SCREENS
// ─────────────────────────────────────────────────────────────

function PlaceholderScreen({ title }: { title: string }) {
  return (
    <SafeAreaView style={placeholderStyles.root} edges={['top']}>
      <View style={placeholderStyles.inner}>
        <Feather name="clock" size={48} color="#3A3B4D" />
        <Text style={placeholderStyles.title}>{title}</Text>
        <Text style={placeholderStyles.subtitle}>Coming soon</Text>
      </View>
    </SafeAreaView>
  );
}

const placeholderStyles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#1A1B2E' },
  inner: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 },
  title: { color: '#FFFFFF', fontSize: 20, fontWeight: '600' },
  subtitle: { color: '#8E8E93', fontSize: 14 },
});

function CallsScreen() {
  return <PlaceholderScreen title="Calls" />;
}

function StoriesScreen() {
  return <PlaceholderScreen title="Stories" />;
}

function SettingsScreen() {
  return <PlaceholderScreen title="Settings" />;
}

function UserProfileScreen() {
  return <PlaceholderScreen title="User Profile" />;
}

function GlobalSearchScreen() {
  return <PlaceholderScreen title="Search" />;
}

function SavedMessagesScreen() {
  return <PlaceholderScreen title="Saved Messages" />;
}

function NotesScreen() {
  return <PlaceholderScreen title="Notes" />;
}

// ─────────────────────────────────────────────────────────────
// TAB NAVIGATOR
// ─────────────────────────────────────────────────────────────

const Tab = createBottomTabNavigator<MainTabParamList>();

type FeatherIconName = React.ComponentProps<typeof Feather>['name'];

const TAB_ICONS: Record<keyof MainTabParamList, FeatherIconName> = {
  Chats: 'message-circle',
  Calls: 'phone',
  Stories: 'aperture',
  Settings: 'settings',
};

function TabNavigator() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarIcon: ({ color, size }) => (
          <Feather name={TAB_ICONS[route.name]} size={size} color={color} />
        ),
        tabBarActiveTintColor: '#7C83FD',
        tabBarInactiveTintColor: '#8E8E93',
        tabBarStyle: {
          backgroundColor: '#1A1B2E',
          borderTopWidth: 0,
          shadowOpacity: 0,
          elevation: 0,
        },
        tabBarLabelStyle: {
          fontSize: 11,
          fontWeight: '500',
          marginBottom: 2,
        },
      })}
    >
      <Tab.Screen name="Chats" component={ChatsScreen} />
      <Tab.Screen name="Calls" component={CallsScreen} />
      <Tab.Screen name="Stories" component={StoriesScreen} />
      <Tab.Screen name="Settings" component={SettingsScreen} />
    </Tab.Navigator>
  );
}

// ─────────────────────────────────────────────────────────────
// ROOT STACK (tabs + overlay screens)
// ─────────────────────────────────────────────────────────────

const Stack = createNativeStackNavigator<RootStackParamList>();

export function MainNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      {/* Tab bar host */}
      <Stack.Screen name="Main" component={TabNavigator} />

      {/* Full-screen overlays (slide over tabs) */}
      <Stack.Screen
        name="Messages"
        component={MessagesScreen}
        options={{ animation: 'slide_from_right' }}
      />
      <Stack.Screen
        name="UserProfile"
        component={UserProfileScreen}
        options={{ animation: 'slide_from_right' }}
      />
      <Stack.Screen
        name="GlobalSearch"
        component={GlobalSearchScreen}
        options={{ animation: 'fade' }}
      />
      <Stack.Screen
        name="SavedMessages"
        component={SavedMessagesScreen}
        options={{ animation: 'slide_from_right' }}
      />
      <Stack.Screen
        name="Notes"
        component={NotesScreen}
        options={{ animation: 'slide_from_right' }}
      />
    </Stack.Navigator>
  );
}
