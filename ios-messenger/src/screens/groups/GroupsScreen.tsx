import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  TextInput,
  StyleSheet,
  Alert,
  RefreshControl,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';

import { useTheme } from '../../theme';
import { useTranslation } from '../../i18n';
import type { RootStackParamList } from '../../navigation/types';

type GroupsNavigationProp = NativeStackNavigationProp<RootStackParamList>;

interface GroupItem {
  id: string;
  name: string;
  description?: string;
  memberCount: number;
  onlineCount: number;
  lastActivity: Date;
  isAdmin?: boolean;
  type: 'public' | 'private';
  avatarColor: string;
  unreadCount?: number;
}

const PALETTE = ['#1565C0', '#0077B6', '#2E7D32', '#6A1B9A', '#E65100', '#AD1457', '#00695C'];

const MOCK_GROUPS: GroupItem[] = [
  {
    id: 'g1',
    name: 'DevTeam Ukraine',
    description: 'Команда розробників України',
    memberCount: 124,
    onlineCount: 18,
    lastActivity: new Date(Date.now() - 1000 * 60 * 5),
    isAdmin: true,
    type: 'private',
    avatarColor: PALETTE[0],
    unreadCount: 7,
  },
  {
    id: 'g2',
    name: 'React Native UA',
    description: 'Спільнота React Native розробників',
    memberCount: 532,
    onlineCount: 41,
    lastActivity: new Date(Date.now() - 1000 * 60 * 20),
    type: 'public',
    avatarColor: PALETTE[1],
    unreadCount: 23,
  },
  {
    id: 'g3',
    name: 'WorldMates Beta',
    description: 'Бета-тестування WorldMates',
    memberCount: 89,
    onlineCount: 12,
    lastActivity: new Date(Date.now() - 1000 * 60 * 60),
    isAdmin: true,
    type: 'private',
    avatarColor: PALETTE[2],
  },
  {
    id: 'g4',
    name: 'Kyiv Tech Hub',
    description: 'Технологічне ком\'юніті Києва',
    memberCount: 1240,
    onlineCount: 87,
    lastActivity: new Date(Date.now() - 1000 * 60 * 90),
    type: 'public',
    avatarColor: PALETTE[3],
    unreadCount: 156,
  },
  {
    id: 'g5',
    name: 'Сімейний чат',
    memberCount: 12,
    onlineCount: 3,
    lastActivity: new Date(Date.now() - 1000 * 60 * 60 * 3),
    type: 'private',
    avatarColor: PALETTE[4],
  },
  {
    id: 'g6',
    name: 'Друзі по університету',
    memberCount: 34,
    onlineCount: 5,
    lastActivity: new Date(Date.now() - 1000 * 60 * 60 * 24),
    type: 'private',
    avatarColor: PALETTE[5],
  },
  {
    id: 'g7',
    name: 'Open Source UA',
    description: 'Open source проекти та контриб\'ютори',
    memberCount: 876,
    onlineCount: 62,
    lastActivity: new Date(Date.now() - 1000 * 60 * 60 * 48),
    type: 'public',
    avatarColor: PALETTE[6],
  },
];

function formatLastActivity(date: Date): string {
  const diff = (Date.now() - date.getTime()) / 1000;
  if (diff < 60) return 'Щойно';
  if (diff < 3600) return `${Math.floor(diff / 60)} хв тому`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} год тому`;
  return date.toLocaleDateString('uk-UA', { day: 'numeric', month: 'short' });
}

function getInitials(name: string): string {
  return name
    .split(' ')
    .map((w) => w[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);
}

interface GroupRowProps {
  item: GroupItem;
  onPress: (item: GroupItem) => void;
  onLeave: (item: GroupItem) => void;
}

function GroupRow({ item, onPress, onLeave }: GroupRowProps) {
  const theme = useTheme();
  const { t } = useTranslation();

  return (
    <TouchableOpacity
      style={[styles.row, { backgroundColor: theme.surface }]}
      onPress={() => onPress(item)}
      onLongPress={() => {
        Alert.alert(item.name, '', [
          { text: t('view_profile'), onPress: () => onPress(item) },
          { text: item.isAdmin ? t('delete_group') : t('leave_group'), style: 'destructive', onPress: () => onLeave(item) },
          { text: t('cancel'), style: 'cancel' },
        ]);
      }}
      activeOpacity={0.7}
    >
      {/* Avatar */}
      <View style={[styles.avatar, { backgroundColor: item.avatarColor }]}>
        <Text style={styles.avatarInitials}>{getInitials(item.name)}</Text>
        {item.type === 'public' && (
          <View style={[styles.publicBadge, { backgroundColor: theme.background, borderColor: item.avatarColor }]}>
            <Feather name="globe" size={8} color={item.avatarColor} />
          </View>
        )}
      </View>

      {/* Info */}
      <View style={styles.info}>
        <View style={styles.infoTop}>
          <Text style={[styles.groupName, { color: theme.text }]} numberOfLines={1}>
            {item.name}
          </Text>
          <View style={styles.infoRight}>
            {item.isAdmin && (
              <View style={[styles.adminBadge, { backgroundColor: theme.primary + '22' }]}>
                <Text style={[styles.adminBadgeText, { color: theme.primary }]}>{t('admin')}</Text>
              </View>
            )}
            <Text style={[styles.lastActivity, { color: theme.textTertiary }]}>
              {formatLastActivity(item.lastActivity)}
            </Text>
          </View>
        </View>

        <View style={styles.infoBottom}>
          <View style={styles.memberInfo}>
            <Feather name="users" size={12} color={theme.textTertiary} />
            <Text style={[styles.memberText, { color: theme.textTertiary }]}>
              {item.memberCount.toLocaleString()} {t('group_members_count')}
            </Text>
            <View style={[styles.onlineDot, { backgroundColor: theme.success }]} />
            <Text style={[styles.onlineText, { color: theme.success }]}>
              {item.onlineCount} {t('group_online_count')}
            </Text>
          </View>

          {item.unreadCount ? (
            <View
              style={[
                styles.unreadBadge,
                { backgroundColor: theme.primary },
              ]}
            >
              <Text style={[styles.unreadText, { color: theme.white }]}>
                {item.unreadCount > 99 ? '99+' : item.unreadCount}
              </Text>
            </View>
          ) : null}
        </View>
      </View>
    </TouchableOpacity>
  );
}

export function GroupsScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  const navigation = useNavigation<GroupsNavigationProp>();

  const [groups, setGroups] = useState<GroupItem[]>(MOCK_GROUPS);
  const [query, setQuery] = useState('');
  const [refreshing, setRefreshing] = useState(false);

  const filtered = query.trim()
    ? groups.filter((g) => g.name.toLowerCase().includes(query.toLowerCase()))
    : groups;

  const handleRefresh = useCallback(() => {
    setRefreshing(true);
    setTimeout(() => setRefreshing(false), 800);
  }, []);

  const handleGroupPress = useCallback(
    (item: GroupItem) => {
      navigation.navigate('GroupMessages', {
        groupId: item.id,
        groupName: item.name,
      });
    },
    [navigation],
  );

  const handleLeave = useCallback(
    (item: GroupItem) => {
      Alert.alert(
        item.isAdmin ? t('delete_group') : t('leave_group'),
        t('delete_chat_confirm'),
        [
          { text: t('cancel'), style: 'cancel' },
          {
            text: item.isAdmin ? t('delete') : t('leave_group'),
            style: 'destructive',
            onPress: () => setGroups((prev) => prev.filter((g) => g.id !== item.id)),
          },
        ],
      );
    },
    [t],
  );

  const handleCreate = useCallback(() => {
    Alert.alert(t('create_group'), t('coming_soon'));
  }, [t]);

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: theme.background }]} edges={['top']}>
      {/* Header */}
      <View style={[styles.header, { borderBottomColor: theme.divider }]}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
        >
          <Feather name="arrow-left" size={22} color={theme.primary} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: theme.text }]}>{t('groups')}</Text>
        <TouchableOpacity
          onPress={handleCreate}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          style={[styles.createBtn, { backgroundColor: theme.primary }]}
        >
          <Feather name="plus" size={18} color={theme.white} />
        </TouchableOpacity>
      </View>

      {/* Search */}
      <View style={[styles.searchBar, { backgroundColor: theme.inputBackground }]}>
        <Feather name="search" size={16} color={theme.textTertiary} />
        <TextInput
          style={[styles.searchInput, { color: theme.text }]}
          placeholder={t('search_groups')}
          placeholderTextColor={theme.textTertiary}
          value={query}
          onChangeText={setQuery}
          autoCapitalize="none"
          autoCorrect={false}
          selectionColor={theme.primary}
        />
        {query.length > 0 && (
          <TouchableOpacity onPress={() => setQuery('')}>
            <Feather name="x" size={16} color={theme.textTertiary} />
          </TouchableOpacity>
        )}
      </View>

      {filtered.length === 0 ? (
        <View style={styles.empty}>
          <View style={[styles.emptyIcon, { backgroundColor: theme.surface }]}>
            <Feather name="users" size={36} color={theme.textTertiary} />
          </View>
          <Text style={[styles.emptyText, { color: theme.textSecondary }]}>{t('no_groups')}</Text>
          <TouchableOpacity
            style={[styles.createGroupBtn, { backgroundColor: theme.primary }]}
            onPress={handleCreate}
            activeOpacity={0.85}
          >
            <Feather name="plus" size={16} color={theme.white} />
            <Text style={[styles.createGroupBtnText, { color: theme.white }]}>{t('create_group_btn')}</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={filtered}
          keyExtractor={(item) => item.id}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={handleRefresh}
              tintColor={theme.primary}
              colors={[theme.primary]}
            />
          }
          renderItem={({ item, index }) => (
            <>
              <GroupRow item={item} onPress={handleGroupPress} onLeave={handleLeave} />
              {index < filtered.length - 1 && (
                <View style={[styles.separator, { backgroundColor: theme.divider, marginLeft: 76 }]} />
              )}
            </>
          )}
          showsVerticalScrollIndicator={false}
          contentContainerStyle={styles.listContent}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    gap: 12,
  },
  headerTitle: { flex: 1, fontSize: 20, fontWeight: '700' },
  createBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: 16,
    marginVertical: 10,
    borderRadius: 12,
    paddingHorizontal: 12,
    height: 40,
    gap: 8,
  },
  searchInput: { flex: 1, fontSize: 15, paddingVertical: 0 },
  listContent: { paddingBottom: 24 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  avatar: {
    width: 52,
    height: 52,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  avatarInitials: { fontSize: 20, fontWeight: '700', color: '#fff' },
  publicBadge: {
    position: 'absolute',
    bottom: 0,
    right: 0,
    width: 16,
    height: 16,
    borderRadius: 8,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  info: { flex: 1, marginLeft: 12 },
  infoTop: { flexDirection: 'row', alignItems: 'center', marginBottom: 4 },
  groupName: { flex: 1, fontSize: 15, fontWeight: '600' },
  infoRight: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  adminBadge: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
  },
  adminBadgeText: { fontSize: 10, fontWeight: '700' },
  lastActivity: { fontSize: 12 },
  infoBottom: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  memberInfo: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  memberText: { fontSize: 12 },
  onlineDot: { width: 6, height: 6, borderRadius: 3 },
  onlineText: { fontSize: 12 },
  unreadBadge: {
    minWidth: 20,
    height: 20,
    borderRadius: 10,
    paddingHorizontal: 5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  unreadText: { fontSize: 11, fontWeight: '700' },
  separator: { height: StyleSheet.hairlineWidth },
  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 16,
    paddingBottom: 40,
  },
  emptyIcon: {
    width: 80,
    height: 80,
    borderRadius: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: { fontSize: 16, fontWeight: '500' },
  createGroupBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 12,
    gap: 8,
    marginTop: 8,
  },
  createGroupBtnText: { fontSize: 15, fontWeight: '600' },
});
