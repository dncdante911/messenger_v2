import React, { useCallback, useEffect } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  TouchableHighlight,
  ScrollView,
  StyleSheet,
  Alert,
  ActivityIndicator,
  RefreshControl,
  StatusBar,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Feather } from '@expo/vector-icons';
import { format, isToday, isYesterday, isThisWeek } from 'date-fns';

import { Avatar } from '../../components/common/Avatar';
import { Badge } from '../../components/common/Badge';
import { useChatStore } from '../../store/chatStore';
import { usePresenceStore } from '../../services/presenceService';
import { useTheme } from '../../theme';
import { useTranslation } from '../../i18n';
import type { Chat, Message } from '../../api/types';
import type { RootStackParamList } from '../../navigation/types';
import { MESSAGE_TYPES } from '../../constants/api';

type ChatsNavProp = NativeStackNavigationProp<RootStackParamList>;

function formatChatTime(ts: string | number | undefined): string {
  if (!ts) return '';
  const date = typeof ts === 'number' ? new Date(ts) : new Date(ts);
  if (isNaN(date.getTime())) return '';
  if (isToday(date)) return format(date, 'HH:mm');
  if (isYesterday(date)) return 'Вчора';
  if (isThisWeek(date)) return format(date, 'EEE');
  return format(date, 'dd/MM/yy');
}

function messagePreview(msg: Message | undefined, t: (k: string) => string): string {
  if (!msg) return '';
  if (msg.isDeleted) return t('message_deleted');
  switch (msg.type) {
    case MESSAGE_TYPES.IMAGE: return '📷 ' + t('photo');
    case MESSAGE_TYPES.VIDEO: return '🎥 ' + t('video');
    case MESSAGE_TYPES.VOICE: return '🎤 ' + t('voice_message');
    case MESSAGE_TYPES.AUDIO: return '🎵 ' + t('audio');
    case MESSAGE_TYPES.FILE: return '📎 ' + t('file');
    case MESSAGE_TYPES.LOCATION: return '📍 ' + t('location');
    case MESSAGE_TYPES.CALL: return '📞 ' + t('call');
    default: return msg.decryptedText ?? msg.text ?? '';
  }
}

interface StoryItem {
  id: string;
  name: string;
  avatar?: string;
  isOwn?: boolean;
}

const PLACEHOLDER_STORIES: StoryItem[] = [
  { id: 'own', name: 'Моя Історія', isOwn: true },
  { id: 's1', name: 'Alex M.' },
  { id: 's2', name: 'Sara K.' },
  { id: 's3', name: 'Jordan' },
];

const StoryAvatar: React.FC<{ item: StoryItem }> = ({ item }) => {
  const theme = useTheme();
  return (
    <TouchableOpacity style={styles.storyItem} activeOpacity={0.7}>
      <View style={[styles.storyRing, { borderColor: theme.primary }]}>
        <Avatar uri={item.avatar} name={item.name} size={52} />
        {item.isOwn && (
          <View style={[styles.storyAddBadge, { backgroundColor: theme.primary, borderColor: theme.background }]}>
            <Feather name="plus" size={10} color="#FFFFFF" />
          </View>
        )}
      </View>
      <Text style={[styles.storyName, { color: theme.textSecondary }]} numberOfLines={1}>
        {item.name}
      </Text>
    </TouchableOpacity>
  );
};

interface ChatListItemProps {
  chat: Chat;
  isOnline: boolean;
  onPress: () => void;
  onLongPress: () => void;
}

const ChatListItem: React.FC<ChatListItemProps> = ({ chat, isOnline, onPress, onLongPress }) => {
  const theme = useTheme();
  const { t } = useTranslation();
  const preview = messagePreview(chat.lastMessage, t);
  const timeStr = formatChatTime(chat.lastMessage?.createdAt ?? chat.updatedAt);
  const hasUnread = chat.unreadCount > 0;

  return (
    <TouchableHighlight
      onPress={onPress}
      onLongPress={onLongPress}
      underlayColor={theme.surface}
      style={[styles.chatItem, { backgroundColor: theme.background }]}
    >
      <View style={styles.chatItemInner}>
        <View style={styles.avatarWrap}>
          <Avatar
            uri={chat.avatar}
            name={chat.name}
            size={52}
            showOnline
            isOnline={isOnline || !!chat.isOnline}
          />
          {chat.isPinned && (
            <View style={[styles.pinBadge, { backgroundColor: theme.surface, borderColor: theme.background }]}>
              <Feather name="bookmark" size={8} color={theme.primary} />
            </View>
          )}
        </View>

        <View style={styles.chatContent}>
          <View style={styles.chatTopRow}>
            <View style={styles.chatNameRow}>
              <Text style={[styles.chatName, { color: theme.text }]} numberOfLines={1}>
                {chat.name}
              </Text>
              {chat.isMuted && (
                <Feather name="bell-off" size={12} color={theme.textTertiary} style={styles.muteIcon} />
              )}
            </View>
            <Text style={[styles.chatTime, { color: hasUnread && !chat.isMuted ? theme.primary : theme.textTertiary }]}>
              {timeStr}
            </Text>
          </View>

          <View style={styles.chatBottomRow}>
            <Text style={[styles.chatPreview, { color: theme.textSecondary }]} numberOfLines={1}>
              {preview}
            </Text>
            <Badge count={chat.unreadCount} muted={chat.isMuted} />
          </View>
        </View>
      </View>
    </TouchableHighlight>
  );
};

export function ChatsScreen() {
  const navigation = useNavigation<ChatsNavProp>();
  const theme = useTheme();
  const { t } = useTranslation();

  const chats = useChatStore((s) => s.chats);
  const isLoadingChats = useChatStore((s) => s.isLoadingChats);
  const onlineUsers = usePresenceStore((s) => s.onlineUsers);
  const loadChats = useChatStore((s) => s.loadChats);
  const archiveChat = useChatStore((s) => s.archiveChat);
  const muteChat = useChatStore((s) => s.muteChat);
  const pinChat = useChatStore((s) => s.pinChat);
  const deleteConversation = useChatStore((s) => s.deleteConversation);

  useEffect(() => {
    loadChats();
  }, [loadChats]);

  const openChat = useCallback(
    (chat: Chat) => {
      navigation.navigate('Messages', {
        chatId: chat.id,
        chatType: chat.type,
        chatName: chat.name,
        chatAvatar: chat.avatar,
        userId: chat.userId,
      });
    },
    [navigation],
  );

  const showChatActions = useCallback(
    (chat: Chat) => {
      const userId = chat.userId ?? chat.id;
      Alert.alert(chat.name, undefined, [
        {
          text: chat.isArchived ? t('unarchive') : t('archive'),
          onPress: () => archiveChat(userId, !chat.isArchived),
        },
        {
          text: chat.isMuted ? t('unmute') : t('mute'),
          onPress: () => muteChat(userId, !chat.isMuted),
        },
        {
          text: chat.isPinned ? t('unpin') : t('pin'),
          onPress: () => pinChat(userId, !chat.isPinned),
        },
        {
          text: t('delete'),
          style: 'destructive',
          onPress: () => {
            Alert.alert(
              t('delete_chat'),
              t('delete_chat_confirm'),
              [
                { text: t('cancel'), style: 'cancel' },
                {
                  text: t('delete'),
                  style: 'destructive',
                  onPress: () => deleteConversation(userId),
                },
              ],
            );
          },
        },
        { text: t('cancel'), style: 'cancel' },
      ]);
    },
    [archiveChat, muteChat, pinChat, deleteConversation, t],
  );

  const keyExtractor = useCallback((item: Chat) => item.id, []);

  const renderItem = useCallback(
    ({ item }: { item: Chat }) => (
      <ChatListItem
        chat={item}
        isOnline={onlineUsers.has(item.userId ?? '')}
        onPress={() => openChat(item)}
        onLongPress={() => showChatActions(item)}
      />
    ),
    [onlineUsers, openChat, showChatActions],
  );

  const ListEmpty = (
    <View style={styles.emptyContainer}>
      <Feather name="message-circle" size={56} color={theme.divider} />
      <Text style={[styles.emptyTitle, { color: theme.text }]}>{t('no_chats_yet')}</Text>
      <Text style={[styles.emptySubtitle, { color: theme.textSecondary }]}>{t('start_conversation')}</Text>
    </View>
  );

  const ListHeader = (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.storyBar}
    >
      {PLACEHOLDER_STORIES.map((s) => (
        <StoryAvatar key={s.id} item={s} />
      ))}
    </ScrollView>
  );

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: theme.background }]} edges={['top']}>
      <StatusBar barStyle="light-content" backgroundColor={theme.background} />

      <View style={[styles.header, { borderBottomColor: theme.divider }]}>
        <Text style={[styles.headerTitle, { color: theme.text }]}>{t('app_name')}</Text>
        <View style={styles.headerActions}>
          <TouchableOpacity
            style={styles.headerBtn}
            onPress={() => navigation.navigate('GlobalSearch')}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Feather name="search" size={22} color={theme.text} />
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.headerBtn}
            onPress={() => Alert.alert(t('new_chat'), t('coming_soon'))}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Feather name="edit" size={22} color={theme.text} />
          </TouchableOpacity>
        </View>
      </View>

      <FlatList
        data={chats}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        ListHeaderComponent={ListHeader}
        ListEmptyComponent={isLoadingChats ? null : ListEmpty}
        refreshControl={
          <RefreshControl
            refreshing={isLoadingChats}
            onRefresh={loadChats}
            tintColor={theme.primary}
            colors={[theme.primary]}
          />
        }
        contentContainerStyle={chats.length === 0 ? styles.flatListEmpty : undefined}
        showsVerticalScrollIndicator={false}
        ItemSeparatorComponent={() => (
          <View style={[styles.separator, { backgroundColor: theme.divider }]} />
        )}
      />

      {isLoadingChats && chats.length === 0 && (
        <View style={[styles.loadingOverlay, { backgroundColor: theme.background }]}>
          <ActivityIndicator size="large" color={theme.primary} />
        </View>
      )}

      <TouchableOpacity
        style={[styles.fab, { backgroundColor: theme.primary, shadowColor: theme.primary }]}
        onPress={() => Alert.alert(t('new_chat'), t('coming_soon'))}
        activeOpacity={0.85}
      >
        <Feather name="edit-2" size={22} color="#FFFFFF" />
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  headerTitle: { fontSize: 22, fontWeight: '700', letterSpacing: -0.3 },
  headerActions: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  headerBtn: { padding: 4 },
  storyBar: { paddingHorizontal: 12, paddingVertical: 12, gap: 8 },
  storyItem: { alignItems: 'center', width: 64, marginHorizontal: 4 },
  storyRing: {
    width: 58,
    height: 58,
    borderRadius: 29,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 4,
  },
  storyAddBadge: {
    position: 'absolute',
    bottom: -2,
    right: -2,
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  storyName: { fontSize: 11, textAlign: 'center', maxWidth: 60 },
  chatItem: { paddingHorizontal: 16, paddingVertical: 10 },
  chatItemInner: { flexDirection: 'row', alignItems: 'center' },
  avatarWrap: { position: 'relative', marginRight: 12 },
  pinBadge: {
    position: 'absolute',
    bottom: 0,
    right: 0,
    width: 16,
    height: 16,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1.5,
  },
  chatContent: { flex: 1, justifyContent: 'center' },
  chatTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 3,
  },
  chatNameRow: { flexDirection: 'row', alignItems: 'center', flex: 1, marginRight: 8 },
  chatName: { fontSize: 15, fontWeight: '600', flexShrink: 1 },
  muteIcon: { marginLeft: 4 },
  chatTime: { fontSize: 12, flexShrink: 0 },
  chatBottomRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  chatPreview: { fontSize: 13, flex: 1, marginRight: 8 },
  separator: { height: StyleSheet.hairlineWidth, marginLeft: 80 },
  flatListEmpty: { flexGrow: 1 },
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingTop: 60,
    paddingHorizontal: 32,
  },
  emptyTitle: { fontSize: 18, fontWeight: '600', marginTop: 16 },
  emptySubtitle: { fontSize: 14, marginTop: 6, textAlign: 'center' },
  loadingOverlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fab: {
    position: 'absolute',
    bottom: 24,
    right: 20,
    width: 52,
    height: 52,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 8,
    elevation: 8,
  },
});
