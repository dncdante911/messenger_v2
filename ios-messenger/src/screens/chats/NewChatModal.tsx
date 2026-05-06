import React, { useState, useCallback, useRef } from 'react';
import {
  View,
  Text,
  Modal,
  TextInput,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
  SafeAreaView,
  ListRenderItemInfo,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import { useTheme } from '../../theme';
import { useTranslation } from '../../i18n';
import { Avatar } from '../../components/common/Avatar';
import { searchUsers, type UserSearchResult } from '../../api/chatApi';

interface Props {
  visible: boolean;
  onClose: () => void;
  onSelectUser: (user: UserSearchResult) => void;
}

export function NewChatModal({ visible, onClose, onSelectUser }: Props) {
  const theme = useTheme();
  const { t } = useTranslation();

  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserSearchResult[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const doSearch = useCallback(async (q: string) => {
    const trimmed = q.trim();
    if (trimmed.length < 2) {
      setResults([]);
      setSearched(false);
      return;
    }
    setIsLoading(true);
    try {
      const found = await searchUsers(trimmed);
      setResults(found);
      setSearched(true);
    } catch {
      setResults([]);
      setSearched(true);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const handleChangeText = useCallback((text: string) => {
    setQuery(text);
    if (debounceTimer.current) clearTimeout(debounceTimer.current);
    debounceTimer.current = setTimeout(() => doSearch(text), 400);
  }, [doSearch]);

  const handleClose = useCallback(() => {
    setQuery('');
    setResults([]);
    setSearched(false);
    onClose();
  }, [onClose]);

  const renderItem = useCallback(({ item }: ListRenderItemInfo<UserSearchResult>) => (
    <TouchableOpacity
      style={[styles.userItem, { backgroundColor: theme.surface }]}
      onPress={() => {
        handleClose();
        onSelectUser(item);
      }}
      activeOpacity={0.75}
    >
      <Avatar uri={item.avatar} name={item.name} size={44} showOnline isOnline={item.isOnline} />
      <View style={styles.userInfo}>
        <Text style={[styles.userName, { color: theme.text }]} numberOfLines={1}>
          {item.name}
        </Text>
        {item.username ? (
          <Text style={[styles.userHandle, { color: theme.textTertiary }]} numberOfLines={1}>
            @{item.username}
          </Text>
        ) : null}
      </View>
      <Feather name="chevron-right" size={18} color={theme.textTertiary} />
    </TouchableOpacity>
  ), [theme, handleClose, onSelectUser]);

  return (
    <Modal
      visible={visible}
      animationType="slide"
      presentationStyle="pageSheet"
      onRequestClose={handleClose}
    >
      <SafeAreaView style={[styles.root, { backgroundColor: theme.background }]}>
        {/* Header */}
        <View style={[styles.header, { borderBottomColor: theme.divider }]}>
          <TouchableOpacity onPress={handleClose} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
            <Text style={[styles.cancelBtn, { color: theme.primary }]}>{t('cancel')}</Text>
          </TouchableOpacity>
          <Text style={[styles.headerTitle, { color: theme.text }]}>{t('new_chat')}</Text>
          <View style={styles.headerSpacer} />
        </View>

        {/* Search input */}
        <View style={[styles.searchRow, { backgroundColor: theme.surface }]}>
          <Feather name="search" size={16} color={theme.textTertiary} style={styles.searchIcon} />
          <TextInput
            style={[styles.searchInput, { color: theme.text }]}
            placeholder={t('search_users')}
            placeholderTextColor={theme.textTertiary}
            value={query}
            onChangeText={handleChangeText}
            autoFocus
            autoCapitalize="none"
            autoCorrect={false}
            returnKeyType="search"
            onSubmitEditing={() => doSearch(query)}
          />
          {query.length > 0 && (
            <TouchableOpacity onPress={() => handleChangeText('')}>
              <Feather name="x-circle" size={16} color={theme.textTertiary} />
            </TouchableOpacity>
          )}
        </View>

        {/* Results */}
        {isLoading ? (
          <View style={styles.center}>
            <ActivityIndicator color={theme.primary} />
          </View>
        ) : searched && results.length === 0 ? (
          <View style={styles.center}>
            <Feather name="user-x" size={40} color={theme.divider} />
            <Text style={[styles.emptyText, { color: theme.textSecondary }]}>{t('no_users_found')}</Text>
          </View>
        ) : (
          <FlatList
            data={results}
            keyExtractor={(item) => item.id}
            renderItem={renderItem}
            contentContainerStyle={styles.list}
            ItemSeparatorComponent={() => (
              <View style={[styles.separator, { backgroundColor: theme.divider }]} />
            )}
            keyboardShouldPersistTaps="handled"
          />
        )}
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  headerTitle: { fontSize: 17, fontWeight: '600' },
  cancelBtn: { fontSize: 16 },
  headerSpacer: { width: 50 },
  searchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: 16,
    marginVertical: 12,
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  searchIcon: { marginRight: 8 },
  searchInput: { flex: 1, fontSize: 16, paddingVertical: 0 },
  list: { paddingHorizontal: 16, paddingTop: 4 },
  userItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderRadius: 12,
    gap: 12,
  },
  userInfo: { flex: 1 },
  userName: { fontSize: 15, fontWeight: '600' },
  userHandle: { fontSize: 13, marginTop: 2 },
  separator: { height: StyleSheet.hairlineWidth, marginLeft: 68 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 },
  emptyText: { fontSize: 15 },
});
