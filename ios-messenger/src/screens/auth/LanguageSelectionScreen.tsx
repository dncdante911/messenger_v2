import React, { useState } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  StatusBar,
  SafeAreaView,
  ListRenderItemInfo,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { AuthStackParamList } from '../../navigation/types';
import { storageService } from '../../services/storageService';

type LanguageNavigationProp = NativeStackNavigationProp<AuthStackParamList, 'LanguageSelection'>;

interface Language {
  code: string;
  flag: string;
  name: string;
  nativeName: string;
}

const LANGUAGES: Language[] = [
  { code: 'en', flag: '🇬🇧', name: 'English', nativeName: 'English' },
  { code: 'uk', flag: '🇺🇦', name: 'Ukrainian', nativeName: 'Українська' },
  { code: 'ru', flag: '🇷🇺', name: 'Russian', nativeName: 'Русский' },
  { code: 'de', flag: '🇩🇪', name: 'German', nativeName: 'Deutsch' },
  { code: 'es', flag: '🇪🇸', name: 'Spanish', nativeName: 'Español' },
  { code: 'fr', flag: '🇫🇷', name: 'French', nativeName: 'Français' },
  { code: 'pl', flag: '🇵🇱', name: 'Polish', nativeName: 'Polski' },
  { code: 'pt', flag: '🇵🇹', name: 'Portuguese', nativeName: 'Português' },
];

export function LanguageSelectionScreen() {
  const navigation = useNavigation<LanguageNavigationProp>();
  const [selectedCode, setSelectedCode] = useState<string>('en');
  const [isSaving, setIsSaving] = useState(false);

  const handleContinue = async () => {
    if (isSaving) return;
    setIsSaving(true);
    try {
      await storageService.setLanguage(selectedCode);
      await storageService.setFirstLaunchDone();
      navigation.replace('Login');
    } catch {
      setIsSaving(false);
    }
  };

  const renderItem = ({ item }: ListRenderItemInfo<Language>) => {
    const isSelected = item.code === selectedCode;
    return (
      <TouchableOpacity
        style={[styles.languageItem, isSelected && styles.languageItemSelected]}
        onPress={() => setSelectedCode(item.code)}
        activeOpacity={0.7}
      >
        <Text style={styles.flag}>{item.flag}</Text>
        <View style={styles.languageTextContainer}>
          <Text style={styles.languageName}>{item.name}</Text>
          {item.name !== item.nativeName && (
            <Text style={styles.nativeName}>{item.nativeName}</Text>
          )}
        </View>
        {isSelected && (
          <View style={styles.checkmark}>
            <Text style={styles.checkmarkText}>✓</Text>
          </View>
        )}
      </TouchableOpacity>
    );
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor="#1A1B2E" />
      <View style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.title}>Choose your language</Text>
          <Text style={styles.subtitle}>Виберіть мову</Text>
        </View>

        <FlatList
          data={LANGUAGES}
          keyExtractor={(item) => item.code}
          renderItem={renderItem}
          style={styles.list}
          contentContainerStyle={styles.listContent}
          showsVerticalScrollIndicator={false}
          ItemSeparatorComponent={() => <View style={styles.separator} />}
        />

        <View style={styles.footer}>
          <TouchableOpacity
            style={[styles.continueButton, isSaving && styles.continueButtonDisabled]}
            onPress={handleContinue}
            activeOpacity={0.85}
            disabled={isSaving}
          >
            <Text style={styles.continueButtonText}>Continue</Text>
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#1A1B2E',
  },
  container: {
    flex: 1,
    backgroundColor: '#1A1B2E',
  },
  header: {
    paddingHorizontal: 24,
    paddingTop: 40,
    paddingBottom: 24,
  },
  title: {
    color: '#FFFFFF',
    fontSize: 26,
    fontWeight: '700',
    marginBottom: 6,
  },
  subtitle: {
    color: '#8A8FA8',
    fontSize: 16,
    fontWeight: '400',
  },
  list: {
    flex: 1,
  },
  listContent: {
    paddingHorizontal: 16,
    paddingBottom: 8,
  },
  languageItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderRadius: 14,
    backgroundColor: '#23243A',
  },
  languageItemSelected: {
    backgroundColor: '#2E2F4A',
    borderWidth: 1.5,
    borderColor: '#7C83FD',
  },
  flag: {
    fontSize: 28,
    marginRight: 16,
  },
  languageTextContainer: {
    flex: 1,
  },
  languageName: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  nativeName: {
    color: '#8A8FA8',
    fontSize: 13,
    marginTop: 2,
  },
  checkmark: {
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: '#7C83FD',
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkmarkText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
  },
  separator: {
    height: 8,
  },
  footer: {
    paddingHorizontal: 24,
    paddingVertical: 20,
    paddingBottom: 32,
  },
  continueButton: {
    backgroundColor: '#7C83FD',
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: 'center',
    shadowColor: '#7C83FD',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 12,
    elevation: 6,
  },
  continueButtonDisabled: {
    opacity: 0.6,
  },
  continueButtonText: {
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
});
