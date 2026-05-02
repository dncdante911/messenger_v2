// ============================================================
// WorldMates Messenger — Chats List Screen
//
// Main screen after login:
//   • Header with title + search + compose icons
//   • Horizontal story bar (Phase 5 stubs)
//   • FlatList of conversations (ChatListItem)
//   • Pull-to-refresh
//   • FAB for new conversation
//   • Long-press bottom-sheet actions (Archive / Mute / Pin / Delete)
// ============================================================

import React, { useCallback, useEffect, useRef } from 'react';
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
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Feather } from '@expo/vector-icons';
import { format, isToday, isYesterday, isThisWeek } from 'date-fns';

import { Avatar } from '../../components/common/Avatar';
import { Badge } from '../../components/common/Badge';
import { useChatStore } from '../../store/chatStore';
import type { Chat, Message } from '../../api/types';
import type { RootStackParamList } from '../../navigation/types';
import { MESSAGE_TYPES } from '../../constants/api';

// ─────────────────────────────────────────────────────────────
// TYPES
// ─────────────────────────────────────────────────────────────

type ChatsNavProp = NativeStackNavigationProp<RootStackParamList>;

// ─────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────

function formatChatTime(ts: string | number | undefined): string {
  if (!ts) return '';
  const date = typeof ts === 'number' ? new Date(ts) : new Date(ts);
  if (isNaN(date.getTime())) return '';
  if (isToday(date)) return format(date, 'HH:mm');
  if (isYesterday(date)) return 'Yesterday';
  if (isThisWeek(date)) return format(date, 'EEE');
  return format(date, 'dd/MM/yy');
}

function messagePreview(msg: Message | undefined): string {
  if (!msg) return '';
  if (msg.isDeleted) return 'Message deleted';
  switch (msg.type) {
    case MESSAGE_TYPES.IMAGE:
      return '📷 Photo';
    case MESSAGE_TYPES.VIDEO:
      return '🎥 Video';
    case MESSAGE_TYPES.VOICE:
      return '🎤 Voice message';
    case MESSAGE_TYPES.AUDIO:
      return '🎵 Audio';
    case MESSAGE_TYPES.FILE:
      return '📎 File';
    case MESSAGE_TYPES.LOCATION:
      return '📍 Location';
    case MESSAGE_TYPES.CALL:
      return '📞 Call';
    default:
      return msg.decryptedText ?? msg.text ?? '';
  }
}

// ─────────────────────────────────────────────────────────────
// STORY BAR ITEM (Phase 5 placeholder)
// ─────────────────────────────────────────────────────────────

interface StoryItem {
  id: string;
  name: string;
  avatar?: string;
  isOwn?: boolean;
}

const PLACEHOLDER_STORIES: StoryItem[] = [
  { id: 'own', name: 'Your Story', isOwn: true },
  { id: 's1', name: 'Alex M.' },
  { id: 's2', name: 'Sara K.' },
  { id: 's3', name: 'Jordan' },
];

const StoryAvatar: React.FC<{ item: StoryItem }> = ({ item }) => (
  <TouchableOpacity style={styles.storyItem} activeOpacity={0.7}>
    <View style={styles.storyRing}>
      <Avatar uri={item.avatar} name={item.name} size={52} />
      {item.isOwn && (
        <View style={styles.storyAddBadge}>
          <Feather name="plus" size={10} color="#FFFFFF" />
        </View>
      )}
    </View>
    <Text style={styles.storyName} numberOfLines={1}>
      {item.name}
    </Text>
  </TouchableOpacity>
);

// ─────────────────────────────────────────────────────────────
// CHAT LIST ITEM
// ─────────────────────────────────────────────────────────────

interface ChatListItemProps {
  chat: Chat;
  isOnline: boolean;
  onPress: () => void;
  onLongPress: () => void;
}

const ChatListItem: React.FC<ChatListItemProps> = ({ chat, isOnline, onPress, onLongPress }) => {
  const preview = messagePreview(chat.lastMessage);
  const timeStr = formatChatTime(chat.lastMessage?.createdAt ?? chat.updatedAt);
  const hasUnread = chat.unreadCount > 0;

  return (
    <TouchableHighlight
      onPress={onPress}
      onLongPress={onLongPress}
      underlayColor="#2A2B3D"
      style={styles.chatItem}
    >
      <View style={styles.chatItemInner}>
        {/* Avatar */}
        <View style={styles.avatarWrap}>
          <Avatar
            uri={chat.avatar}
            name={chat.name}
            size={52}
            showOnline
            isOnline={isOnline || !!chat.isOnline}
          />
          {chat.isPinned && (
            <View style={styles.pinBadge}>
              <Feather name="bookmark" size={8} color="#7C83FD" />
            </View>
          )}
        </View>

        {/* Text content */}
        <View style={styles.chatContent}>
          <View style={styles.chatTopRow}>
            <View style={styles.chatNameRow}>
              <Text style={styles.chatName} numberOfLines={1}>
                {chat.name}
              </Text>
              {chat.isMuted && (
                <Feather
                  name="bell-off"
                  size={12}
                  color="#8E8E93"
                  style={styles.muteIcon}
                />
              )}
            </View>
            <Text
              style={[styles.chatTime, hasUnread && !chat.isMuted && styles.chatTimeUnread]}
            >
              {timeStr}
            </Text>
          </View>

          <View style={styles.chatBottomRow}>
            <Text style={styles.chatPreview} numberOfLines={1}>
              {preview}
            </Text>
            <Badge count={chat.unreadCount} muted={chat.isMuted} />
          </View>
        </View>
      </View>
    </TouchableHighlight>
  );
};

// ─────────────────────────────────────────────────────────────
// CHATS SCREEN
// ─────────────────────────────────────────────────────────────

export function ChatsScreen() {
  const navigation = useNavigation<ChatsNavProp>();

  const chats = useChatStore((s) => s.chats);
  const isLoadingChats = useChatStore((s) => s.isLoadingChats);
  const onlineUsers = useChatStore((s) => s.onlineUsers);
  const loadChats = useChatStore((s) => s.loadChats);
  const archiveChat = useChatStore((s) => s.archiveChat);
  const muteChat = useChatStore((s) => s.muteChat);
  const pinChat = useChatStore((s) => s.pinChat);
  const deleteConversation = useChatStore((s) => s.deleteConversation);

  // Initial load
  useEffect(() => {
    loadChats();
  }, [loadChats]);

  // ── Navigation ───────────────────────────────────────────────
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

  // ── Long-press actions ───────────────────────────────────────
  const showChatActions = useCallback(
    (chat: Chat) => {
      const userId = chat.userId ?? chat.id;

      Alert.alert(chat.name, undefined, [
        {
          text: chat.isArchived ? 'Unarchive' : 'Archive',
          onPress: () => archiveChat(userId, !chat.isArchived),
        },
        {
          text: chat.isMuted ? 'Unmute' : 'Mute',
          onPress: () => muteChat(userId, !chat.isMuted),
        },
        {
          text: chat.isPinned ? 'Unpin' : 'Pin',
          onPress: () => pinChat(userId, !chat.isPinned),
        },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => {
            Alert.alert(
              'Delete Conversation',
              `Delete your conversation with ${chat.name}? This cannot be undone.`,
              [
                { text: 'Cancel', style: 'cancel' },
                {
                  text: 'Delete',
                  style: 'destructive',
                  onPress: () => deleteConversation(userId),
                },
              ],
            );
          },
        },
        { text: 'Cancel', style: 'cancel' },
      ]);
    },
    [archiveChat, muteChat, pinChat, deleteConversation],
  );

  // ── Render helpers ───────────────────────────────────────────
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
      <Feather name="message-circle" size={56} color="#3A3B4D" />
      <Text style={styles.emptyTitle}>No chats yet</Text>
      <Text style={styles.emptySubtitle}>Start a conversation!</Text>
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
    <SafeAreaView style={styles.root} edges={['top']}>
      <StatusBar barStyle="light-content" backgroundColor="#1A1B2E" />

      {/* ── Header ── */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>WorldMates</Text>
        <View style={styles.headerActions}>
          <TouchableOpacity
            style={styles.headerBtn}
            onPress={() => navigation.navigate('GlobalSearch')}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Feather name="search" size={22} color="#FFFFFF" />
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.headerBtn}
            onPress={() => Alert.alert('New Chat', 'New chat coming in next phase.')}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Feather name="edit" size={22} color="#FFFFFF" />
          </TouchableOpacity>
        </View>
      </View>

      {/* ── Chats FlatList ── */}
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
            tintColor="#7C83FD"
            colors={['#7C83FD']}
          />
        }
        contentContainerStyle={chats.length === 0 ? styles.flatListEmpty : undefined}
        showsVerticalScrollIndicator={false}
        ItemSeparatorComponent={() => <View style={styles.separator} />}
      />

      {/* ── Loading overlay (first load only) ── */}
      {isLoadingChats && chats.length === 0 && (
        <View style={styles.loadingOverlay}>
          <ActivityIndicator size="large" color="#7C83FD" />
        </View>
      )}

      {/* ── FAB ── */}
      <TouchableOpacity
        style={styles.fab}
        onPress={() => Alert.alert('New Chat', 'New chat coming in next phase.')}
        activeOpacity={0.85}
      >
        <Feather name="edit-2" size={22} color="#FFFFFF" />
      </TouchableOpacity>
    </SafeAreaView>
  );
}

// ─────────────────────────────────────────────────────────────
// STYLES
// ─────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#1A1B2E',
  },

  // Header
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#2A2B3D',
  },
  headerTitle: {
    color: '#FFFFFF',
    fontSize: 22,
    fontWeight: '700',
    letterSpacing: -0.3,
  },
  headerActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  headerBtn: {
    padding: 4,
  },

  // Story bar
  storyBar: {
    paddingHorizontal: 12,
    paddingVertical: 12,
    gap: 8,
  },
  storyItem: {
    alignItems: 'center',
    width: 64,
    marginHorizontal: 4,
  },
  storyRing: {
    width: 58,
    height: 58,
    borderRadius: 29,
    borderWidth: 2,
    borderColor: '#7C83FD',
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
    backgroundColor: '#7C83FD',
    borderWidth: 2,
    borderColor: '#1A1B2E',
    alignItems: 'center',
    justifyContent: 'center',
  },
  storyName: {
    color: '#8E8E93',
    fontSize: 11,
    textAlign: 'center',
    maxWidth: 60,
  },

  // Chat item
  chatItem: {
    backgroundColor: '#1A1B2E',
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  chatItemInner: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  avatarWrap: {
    position: 'relative',
    marginRight: 12,
  },
  pinBadge: {
    position: 'absolute',
    bottom: 0,
    right: 0,
    width: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: '#2A2B3D',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1.5,
    borderColor: '#1A1B2E',
  },
  chatContent: {
    flex: 1,
    justifyContent: 'center',
  },
  chatTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 3,
  },
  chatNameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
    marginRight: 8,
  },
  chatName: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '600',
    flexShrink: 1,
  },
  muteIcon: {
    marginLeft: 4,
  },
  chatTime: {
    color: '#8E8E93',
    fontSize: 12,
    flexShrink: 0,
  },
  chatTimeUnread: {
    color: '#7C83FD',
    fontWeight: '600',
  },
  chatBottomRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  chatPreview: {
    color: '#8E8E93',
    fontSize: 13,
    flex: 1,
    marginRight: 8,
  },

  // Separator
  separator: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: '#2A2B3D',
    marginLeft: 80,
  },

  // Empty state
  flatListEmpty: {
    flexGrow: 1,
  },
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingTop: 60,
    paddingHorizontal: 32,
  },
  emptyTitle: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '600',
    marginTop: 16,
  },
  emptySubtitle: {
    color: '#8E8E93',
    fontSize: 14,
    marginTop: 6,
    textAlign: 'center',
  },

  // Loading overlay
  loadingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#1A1B2E',
    alignItems: 'center',
    justifyContent: 'center',
  },

  // FAB
  fab: {
    position: 'absolute',
    bottom: 24,
    right: 20,
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: '#7C83FD',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#7C83FD',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 8,
    elevation: 8,
  },
});
