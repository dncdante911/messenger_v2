import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { Feather } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ChatsScreen } from '../screens/chats/ChatsScreen';
import { MessagesScreen } from '../screens/messages/MessagesScreen';
import { useTheme } from '../theme';
import { useTranslation } from '../i18n';
import type { MainTabParamList, RootStackParamList } from './types';

function PlaceholderScreen({ title }: { title: string }) {
  const theme = useTheme();
  return (
    <SafeAreaView style={[styles.placeholderRoot, { backgroundColor: theme.background }]} edges={['top']}>
      <View style={styles.placeholderInner}>
        <Feather name="clock" size={48} color={theme.divider} />
        <Text style={[styles.placeholderTitle, { color: theme.text }]}>{title}</Text>
        <Text style={[styles.placeholderSubtitle, { color: theme.textSecondary }]}>Незабаром</Text>
      </View>
    </SafeAreaView>
  );
}

function CallsScreen() {
  const { t } = useTranslation();
  return <PlaceholderScreen title={t('calls')} />;
}

function StoriesScreen() {
  const { t } = useTranslation();
  return <PlaceholderScreen title={t('stories')} />;
}

function SettingsPlaceholderScreen() {
  const { t } = useTranslation();
  return <PlaceholderScreen title={t('settings')} />;
}

function UserProfilePlaceholderScreen() {
  const { t } = useTranslation();
  return <PlaceholderScreen title={t('profile')} />;
}

function GlobalSearchScreen() {
  const { t } = useTranslation();
  return <PlaceholderScreen title={t('search')} />;
}

function SavedMessagesScreen() {
  const { t } = useTranslation();
  return <PlaceholderScreen title={t('saved_messages')} />;
}

function NotesScreen() {
  const { t } = useTranslation();
  return <PlaceholderScreen title={t('notes')} />;
}

const Tab = createBottomTabNavigator<MainTabParamList>();

type FeatherIconName = React.ComponentProps<typeof Feather>['name'];

const TAB_ICONS: Record<keyof MainTabParamList, FeatherIconName> = {
  Chats: 'message-circle',
  Calls: 'phone',
  Stories: 'aperture',
  Settings: 'settings',
};

function TabNavigator() {
  const theme = useTheme();
  const { t } = useTranslation();

  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarIcon: ({ color, size }) => (
          <Feather name={TAB_ICONS[route.name]} size={size} color={color} />
        ),
        tabBarActiveTintColor: theme.primary,
        tabBarInactiveTintColor: theme.textTertiary,
        tabBarStyle: {
          backgroundColor: theme.tabBar,
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
      <Tab.Screen name="Chats" component={ChatsScreen} options={{ tabBarLabel: t('chats') }} />
      <Tab.Screen name="Calls" component={CallsScreen} options={{ tabBarLabel: t('calls') }} />
      <Tab.Screen name="Stories" component={StoriesScreen} options={{ tabBarLabel: t('stories') }} />
      <Tab.Screen name="Settings" component={SettingsPlaceholderScreen} options={{ tabBarLabel: t('settings') }} />
    </Tab.Navigator>
  );
}

const Stack = createNativeStackNavigator<RootStackParamList>();

export function MainNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Main" component={TabNavigator} />
      <Stack.Screen
        name="Messages"
        component={MessagesScreen}
        options={{ animation: 'slide_from_right' }}
      />
      <Stack.Screen
        name="UserProfile"
        component={UserProfilePlaceholderScreen}
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

const styles = StyleSheet.create({
  placeholderRoot: { flex: 1 },
  placeholderInner: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 },
  placeholderTitle: { fontSize: 20, fontWeight: '600' },
  placeholderSubtitle: { fontSize: 14 },
});
