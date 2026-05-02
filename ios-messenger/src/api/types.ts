// ============================================================
// WorldMates Messenger — Core TypeScript Interfaces
// Ported from Android data model files (User.kt, Group.kt,
// Channel.kt, Story.kt, MessageReaction.kt, Sticker.kt, Bot.kt,
// CallHistory.kt, SharedApiModels.kt)
// ============================================================

import type { MessageType, ChatType, UserRole } from '../constants/api';

// ─────────────────────────────────────────────────────────────
// API RESPONSE WRAPPERS
// ─────────────────────────────────────────────────────────────

/** WoWonder PHP API envelope (api_status: 200 = success) */
export interface ApiResponse<T = unknown> {
  api_status: number | string;
  message?: string;
  data?: T;
  error_code?: number;
  error_message?: string;
  errors?: { error_id?: number; error_text?: string };
}

/** Node.js API envelope */
export interface NodeResponse<T = unknown> {
  success?: boolean;
  api_status?: number;
  data?: T;
  error?: string;
  message?: string;
  error_message?: string;
}

// ─────────────────────────────────────────────────────────────
// USER
// ─────────────────────────────────────────────────────────────

export interface CustomStatus {
  emoji: string;
  text: string;
}

export interface UserDetails {
  postCount?: number;
  albumCount?: number;
  followingCount?: number;
  followersCount?: number;
  groupsCount?: number;
  likesCount?: number;
}

export interface UserRelationship {
  isFollowing: boolean;
  isFollowingMe: boolean;
  followPending: boolean;
  isBlocked: boolean;
  canFollow: boolean;
  canMessage: boolean;
}

export interface UserPrivacySettings {
  followPrivacy: string;
  friendPrivacy: string;
  postPrivacy: string;
  messagePrivacy: string;
  confirmFollowers: string;
  showActivitiesPrivacy: string;
  birthPrivacy: string;
  visitPrivacy: string;
}

export interface User {
  id: string;
  username: string;
  /** Composed display name (firstName + lastName, or username fallback) */
  name: string;
  firstName?: string;
  lastName?: string;
  avatar: string;
  cover?: string;
  email?: string;
  phone?: string;
  about?: string;
  birthday?: string;
  gender?: string;
  website?: string;
  working?: string;
  address?: string;
  city?: string;
  country?: string;
  language?: string;
  timezone?: string;
  isPro: boolean;
  proType?: number;
  proExpiresAt?: string;
  /** 0=none 1=verified(blue) 2=notable(gold) 3=official(green) 4=top-creator(purple) */
  verificationLevel: number;
  isVerified: boolean;
  isOnline: boolean;
  lastSeen?: string;
  lastSeenStatus?: string;
  followersCount: number;
  followingCount: number;
  likesCount?: number;
  groupsCount?: number;
  customStatus?: CustomStatus;
  /** Accent colour hex for profile UI */
  profileAccent?: string;
  profileBadge?: string;
  profileHeaderStyle?: string;
  /** 1 = one of the first 250 registered users */
  isFounder?: boolean;
  balance?: string;
  wallet?: string;
  admin?: boolean;
  details?: UserDetails;
  relationship?: UserRelationship;
}

/** User with auth token — returned after login/register */
export interface AuthUser extends User {
  token: string;
  refreshToken?: string;
}

export interface UserAvatar {
  id: string;
  url: string;
  filePath: string;
  isAnimated: boolean;
  mimeType: string;
  position: number;
  createdAt: number;
}

// ─────────────────────────────────────────────────────────────
// MESSAGE
// ─────────────────────────────────────────────────────────────

export interface MessageReaction {
  emoji: string;
  userId: string;
  count: number;
  hasMyReaction?: boolean;
}

export interface ReactionGroup {
  emoji: string;
  count: number;
  userIds: string[];
  hasMyReaction: boolean;
}

export interface BotInlineButton {
  text: string;
  callbackData?: string;
  url?: string;
  webApp?: { url: string };
}

export interface BotReplyMarkup {
  inlineKeyboard?: BotInlineButton[][];
  keyboard?: Array<Array<{ text: string; requestContact?: boolean; requestLocation?: boolean }>>;
  resizeKeyboard?: boolean;
  oneTimeKeyboard?: boolean;
}

export interface Message {
  id: string;
  fromId: string;
  toId: string;
  /** Group ID — non-null for group messages */
  groupId?: string;
  text: string;
  type: MessageType | string;
  /** Secondary type indicator (e.g. 'poll', 'group_call') */
  typeTwo?: string;
  // Media
  mediaUrl?: string;
  mediaThumb?: string;
  mediaSize?: number;
  mediaDuration?: number;
  mediaType?: string;
  fileName?: string;
  // Sender info (populated in group messages)
  senderName?: string;
  senderAvatar?: string;
  // Reply / thread
  replyToId?: string;
  replyToText?: string;
  replyToName?: string;
  replyToType?: string;
  // State flags
  isEdited: boolean;
  editedTime?: number;
  isDeleted: boolean;
  isSeen: boolean;
  readAt?: number;
  isPinned: boolean;
  // Reactions
  reactions?: MessageReaction[];
  // Encryption (AES-GCM v2 / Signal v3)
  iv?: string;
  tag?: string;
  cipherVersion?: number;
  signalHeader?: string;
  // Stickers
  stickers?: string;
  // Album grouping
  albumId?: string;
  // Location
  lat?: string | number;
  lng?: string | number;
  locationName?: string;
  // Contact card
  contact?: string;
  // Forward
  forwardedFrom?: string;
  forwardedFromName?: string;
  forwardedFromAvatar?: string;
  // Bot
  replyMarkup?: BotReplyMarkup;
  botId?: string;
  // Timestamps
  createdAt: string | number;
  updatedAt?: string | number;
  // Local state (not from server)
  isLocalPending?: boolean;
  decryptedText?: string;
  decryptedMediaUrl?: string;
}

// ─────────────────────────────────────────────────────────────
// CHAT (conversation)
// ─────────────────────────────────────────────────────────────

export interface Chat {
  id: string;
  type: ChatType | string;
  name: string;
  avatar?: string;
  lastMessage?: Message;
  unreadCount: number;
  isArchived: boolean;
  isMuted: boolean;
  isPinned: boolean;
  isHidden: boolean;
  color?: string;
  /** Peer user ID for 1-on-1 chats */
  userId?: string;
  username?: string;
  isOnline?: boolean;
  lastSeen?: string;
  isAdmin?: boolean;
  isBot?: boolean;
  botDescription?: string;
  description?: string;
  membersCount?: number;
  pinnedMessageId?: string;
  updatedAt: string | number;
}

// ─────────────────────────────────────────────────────────────
// GROUP
// ─────────────────────────────────────────────────────────────

export interface GroupSettings {
  allowMembersInvite: boolean;
  allowMembersPin: boolean;
  allowMembersDeleteMessages: boolean;
  allowVoiceCalls: boolean;
  allowVideoCalls: boolean;
  slowModeSeconds: number;
  historyVisibleForNewMembers: boolean;
  historyMessagesCount: number;
  allowMembersSendMedia: boolean;
  allowMembersSendStickers: boolean;
  allowMembersSendGifs: boolean;
  allowMembersSendLinks: boolean;
  allowMembersSendPolls: boolean;
  antiSpamEnabled: boolean;
  maxMessagesPerMinute: number;
  autoMuteSpammers: boolean;
  blockNewUsersMedia: boolean;
  newUserRestrictionHours: number;
}

export interface GroupMember {
  userId: string;
  username: string;
  name?: string;
  avatar: string;
  role: UserRole | string;
  joinedTime: number;
  isMuted: boolean;
  isBlocked: boolean;
  permissions?: string[];
  isOnline?: boolean;
}

export interface Group {
  id: string;
  name: string;
  avatar?: string;
  description?: string;
  membersCount: number;
  adminId: string;
  adminName?: string;
  isPrivate: boolean;
  isAdmin: boolean;
  isModerator: boolean;
  isMember: boolean;
  isMuted: boolean;
  unreadCount: number;
  myRole: UserRole | string;
  createdAt: string | number;
  updatedAt?: string | number;
  members?: GroupMember[];
  pinnedMessageId?: string;
  pinnedMessage?: Message;
  pinnedMessages?: Message[];
  settings?: GroupSettings;
}

export interface GroupJoinRequest {
  id: string;
  groupId: string;
  userId: string;
  username: string;
  userAvatar?: string;
  message?: string;
  status: 'pending' | 'approved' | 'rejected';
  createdTime: number;
  reviewedBy?: string;
  reviewedTime?: number;
}

export interface GroupTopicData {
  id: string;
  name: string;
  description?: string;
  color: string;
  isPrivate: boolean;
  createdAt: number;
  createdBy: string;
  isPinned: boolean;
  isArchived: boolean;
  messageCount: number;
}

export interface GroupStatisticsData {
  membersCount: number;
  messagesCount: number;
  messagesLastWeek: number;
  activeMembers24h: number;
  topSenders?: TopContributor[];
}

export interface TopContributor {
  userId: string;
  username: string;
  name?: string;
  avatar?: string;
  messagesCount: number;
}

export interface GroupCustomizationData {
  groupId: string;
  bubbleStyle: string;
  presetBackground: string;
  accentColor: string;
  enabledByAdmin: boolean;
  updatedAt: number;
  updatedBy: string;
}

export interface AdminLogDto {
  id: string;
  action: string;
  adminId: string;
  adminName: string;
  adminAvatar?: string;
  targetUserId?: string;
  targetUserName?: string;
  targetMessageId?: string;
  details?: Record<string, string>;
  createdAt: string;
}

// ─────────────────────────────────────────────────────────────
// CHANNEL
// ─────────────────────────────────────────────────────────────

export interface ChannelSettings {
  allowComments: boolean;
  allowReactions: boolean;
  allowShares: boolean;
  showStatistics: boolean;
  showViewsCount: boolean;
  notifySubscribersNewPost: boolean;
  autoDeletePostsDays?: number;
  signatureEnabled: boolean;
  commentsModeration: boolean;
  allowForwarding: boolean;
  slowModeSeconds?: number;
  commentIdentity: 'user' | 'channel' | 'user_with_signature';
}

export interface ChannelAdminPermissions {
  canPost: boolean;
  canEditPosts: boolean;
  canDeletePosts: boolean;
  canPinPosts: boolean;
  canEditInfo: boolean;
  canDeleteChannel: boolean;
  canAddAdmins: boolean;
  canRemoveAdmins: boolean;
  canBanUsers: boolean;
  canViewStatistics: boolean;
  canManageComments: boolean;
}

export interface Channel {
  id: string;
  name: string;
  username?: string;
  avatar?: string;
  description?: string;
  subscribersCount: number;
  postsCount: number;
  ownerId: string;
  isPrivate: boolean;
  isVerified: boolean;
  isAdmin: boolean;
  isSubscribed: boolean;
  type: 'public' | 'private';
  category?: string;
  createdAt: string | number;
  settings?: ChannelSettings;
  isPremiumActive?: boolean;
  emojiStatus?: string;
  animatedAvatarUrl?: string;
  channelLevel?: number;
  channelLevelProgress?: number;
}

export interface PostMedia {
  url: string;
  type: 'image' | 'video' | 'audio' | 'file';
  filename?: string;
}

export interface PostReaction {
  emoji: string;
  count: number;
  userReacted: boolean;
  recentUsers?: Array<{ userId: string; username: string; avatar: string }>;
}

export interface InlinePostButton {
  text: string;
  url?: string;
  callback?: string;
}

export interface ChannelPost {
  id: string;
  authorId: string;
  authorUsername?: string;
  authorName?: string;
  authorAvatar?: string;
  text: string;
  poll?: Poll;
  media?: PostMedia[];
  createdAt: string | number;
  isEdited: boolean;
  isPinned: boolean;
  isAd: boolean;
  forwardedFromName?: string;
  forwardedFromAvatar?: string;
  viewsCount: number;
  reactionsCount: number;
  commentsCount: number;
  reactions?: PostReaction[];
  inlineButtons?: InlinePostButton[][];
  collectionTitle?: string;
}

export interface ChannelComment {
  id: string;
  userId: string;
  username?: string;
  userName?: string;
  userAvatar?: string;
  text: string;
  sticker?: string;
  time: number;
  editedTime?: number;
  replyToCommentId?: string;
  reactionsCount: number;
  writtenAsChannel?: boolean;
  channelName?: string;
  channelAvatar?: string;
}

export interface ChannelAdmin {
  userId: string;
  username: string;
  avatar: string;
  role: 'owner' | 'admin' | 'moderator';
  addedTime: number;
  permissions?: ChannelAdminPermissions;
}

export interface ChannelSubscriber {
  id?: string;
  userId?: string;
  username?: string;
  name?: string;
  avatar?: string;
  subscribedTime?: number;
  isMuted: boolean;
  isBanned: boolean;
  role?: string;
  lastSeen?: string;
}

export interface ChannelBannedMember {
  userId: string;
  username?: string;
  name?: string;
  avatar?: string;
  reason?: string;
  banTime: number;
  expireTime: number;
  bannedBy: string;
}

export interface ChannelStatistics {
  subscribersCount: number;
  newSubscribersToday: number;
  newSubscribersWeek: number;
  leftSubscribersWeek: number;
  growthRate: number;
  activeSubscribers24h: number;
  subscribersByDay?: number[];
  postsCount: number;
  postsToday: number;
  postsLastWeek: number;
  postsThisMonth: number;
  viewsTotal: number;
  viewsLastWeek: number;
  avgViewsPerPost: number;
  viewsByDay?: number[];
  reactionsTotal: number;
  commentsTotal: number;
  engagementRate: number;
  mediaPostsCount: number;
  textPostsCount: number;
  peakHours?: number[];
  hourlyViews?: number[];
  topPosts?: Array<{
    id: string;
    text: string;
    views: number;
    reactions: number;
    comments: number;
    publishedTime: number;
    hasMedia: boolean;
  }>;
}

export interface ChannelSubGroupItem {
  id: string;
  name: string;
  avatar?: string;
  description?: string;
  membersCount: number;
  isMember: boolean;
  createdTime?: string;
}

// ─────────────────────────────────────────────────────────────
// STORY
// ─────────────────────────────────────────────────────────────

export interface StoryMedia {
  id: string;
  storyId: string;
  type: 'image' | 'video';
  filename: string;
  expire?: number;
  duration?: number;
}

export interface StoryUser {
  userId: string;
  username: string;
  firstName?: string;
  lastName?: string;
  avatar?: string;
  isPro: boolean;
  isVerified: boolean;
}

export interface StoryReactions {
  like: number;
  love: number;
  haha: number;
  wow: number;
  sad: number;
  angry: number;
  isReacted: boolean;
  type?: string;
  total: number;
}

export interface StoryPollOption {
  id: number;
  text: string;
  votes: number;
}

export interface StoryPoll {
  question: string;
  options: StoryPollOption[];
  votedOptionId?: number;
  totalVotes: number;
}

export interface StoryComment {
  id: string;
  storyId: string;
  userId: string;
  text: string;
  sticker?: string;
  time: number;
  userData?: StoryUser;
  replyToCommentId?: string;
}

export interface StoryViewer {
  userId: string;
  username: string;
  firstName?: string;
  lastName?: string;
  avatar?: string;
  time: number;
}

export interface Story {
  id: string;
  userId: string;
  pageId?: string;
  title?: string;
  description?: string;
  posted: number;
  expire: number;
  thumbnail: string;
  userData?: StoryUser;
  mediaItems: StoryMedia[];
  images?: StoryMedia[];
  videos?: StoryMedia[];
  isOwner: boolean;
  isViewed: boolean;
  viewsCount: number;
  commentsCount: number;
  reactions: StoryReactions;
  musicUrl?: string;
  poll?: StoryPoll;
}

// ─────────────────────────────────────────────────────────────
// CALL HISTORY
// ─────────────────────────────────────────────────────────────

export interface CallUser {
  userId: string;
  username: string;
  name: string;
  avatar?: string;
  isVerified: boolean;
}

export interface CallGroupData {
  groupId: string;
  groupName: string;
  avatar?: string;
  maxParticipants: number;
}

export interface CallHistory {
  id: string;
  callCategory: 'personal' | 'group';
  callType: 'audio' | 'video';
  status: 'ringing' | 'connected' | 'ended' | 'missed' | 'rejected' | 'failed';
  direction: 'incoming' | 'outgoing';
  createdAt: string;
  acceptedAt?: string;
  endedAt?: string;
  duration: number;
  timestamp: number;
  otherUser?: CallUser;
  groupData?: CallGroupData;
}

// ─────────────────────────────────────────────────────────────
// STICKERS
// ─────────────────────────────────────────────────────────────

export interface Sticker {
  id: string;
  packId: string;
  fileUrl: string;
  thumbnailUrl?: string;
  emoji?: string;
  keywords?: string[];
  width?: number;
  height?: number;
  fileSize?: number;
  /** 'webp' | 'png' | 'gif' | 'lottie' | 'static' */
  format?: string;
}

export interface StickerPack {
  id: string;
  name: string;
  description?: string;
  iconUrl?: string;
  thumbnailUrl?: string;
  author?: string;
  stickers?: Sticker[];
  stickerCount?: number;
  isActive: boolean;
  isAnimated: boolean;
  createdAt?: string;
  updatedAt?: string;
}

// ─────────────────────────────────────────────────────────────
// BOT
// ─────────────────────────────────────────────────────────────

export interface BotCommand {
  command: string;
  description: string;
  usageHint?: string;
  scope: string;
}

export interface Bot {
  botId: string;
  username: string;
  displayName: string;
  avatar?: string;
  description?: string;
  about?: string;
  botType: 'standard' | 'verified' | 'system';
  status: 'active' | 'inactive';
  isPublic: boolean;
  isInline: boolean;
  canJoinGroups: boolean;
  supportsCommands: boolean;
  category?: string;
  tags?: string;
  totalUsers: number;
  activeUsers24h: number;
  messagesSent: number;
  messagesReceived: number;
  commandsCount: number;
  commands?: BotCommand[];
  webhookUrl?: string;
  webhookEnabled: boolean;
  webAppUrl?: string;
  linkedUserId?: string;
  createdAt?: string;
  lastActiveAt?: string;
  botToken?: string;
}

export interface BotCallbackQuery {
  id: string;
  from: { id: string; username?: string; firstName?: string; lastName?: string; avatar?: string };
  data: string;
  message?: { messageId: string; date: number; text?: string };
}

export type BotButtonType = 'CALLBACK' | 'URL' | 'WEB_APP';

// ─────────────────────────────────────────────────────────────
// POLLS
// ─────────────────────────────────────────────────────────────

export interface PollOption {
  id: string;
  text: string;
  voteCount: number;
  percent: number;
  isVoted: boolean;
}

export interface Poll {
  id: string;
  question: string;
  pollType: 'regular' | 'quiz';
  isAnonymous: boolean;
  allowsMultipleAnswers: boolean;
  isClosed: boolean;
  totalVotes: number;
  createdBy: string;
  options: PollOption[];
}

// ─────────────────────────────────────────────────────────────
// SAVED MESSAGE / NOTE
// ─────────────────────────────────────────────────────────────

export interface SavedMessage {
  id: string;
  messageId: string;
  chatId: string;
  chatName?: string;
  text?: string;
  mediaUrl?: string;
  type?: string;
  savedAt: string | number;
}

export interface Note {
  id: string;
  text: string;
  mediaUrl?: string;
  mediaType?: string;
  createdAt: string | number;
  updatedAt?: string | number;
  size?: number;
}

// ─────────────────────────────────────────────────────────────
// CHAT FOLDER
// ─────────────────────────────────────────────────────────────

export interface ChatFolder {
  id: string;
  name: string;
  emoji?: string;
  chatIds: string[];
  position: number;
  shareCode?: string;
  createdAt: string | number;
  updatedAt?: string | number;
}

// ─────────────────────────────────────────────────────────────
// SCHEDULED MESSAGE
// ─────────────────────────────────────────────────────────────

export interface ScheduledMessage {
  id: string;
  groupId?: string;
  channelId?: string;
  authorId: string;
  text: string;
  mediaUrl?: string;
  mediaType?: string;
  scheduledTime: number;
  createdTime: number;
  status: 'scheduled' | 'published' | 'failed' | 'cancelled';
  repeatType?: 'none' | 'daily' | 'weekly' | 'monthly';
  isPinned: boolean;
  notifyMembers: boolean;
}

// ─────────────────────────────────────────────────────────────
// DRAFT
// ─────────────────────────────────────────────────────────────

export interface Draft {
  chatId: string;
  text: string;
  replyToId?: string;
  updatedAt: number;
}

// ─────────────────────────────────────────────────────────────
// AUTH REGISTER
// ─────────────────────────────────────────────────────────────

export interface RegisterData {
  username: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  gender?: string;
  birthday?: string;
}

// ─────────────────────────────────────────────────────────────
// MEDIA
// ─────────────────────────────────────────────────────────────

export interface MediaFile {
  id: string;
  url: string;
  type: 'image' | 'video' | 'audio' | 'voice' | 'file';
  mimeType: string;
  size: number;
  duration?: number;
  width?: number;
  height?: number;
  thumbnail?: string;
  createdTime: number;
}

export interface UploadedMedia {
  url: string;
  thumb?: string;
  width?: number;
  height?: number;
  duration?: number;
  size?: number;
  mimeType?: string;
}

// ─────────────────────────────────────────────────────────────
// USER PRESENCE / STATUS
// ─────────────────────────────────────────────────────────────

export interface UserPresenceStatus {
  userId: string;
  isOnline: boolean;
  lastSeen?: number;
  lastSeenStatus?: string;
  customStatus?: CustomStatus;
}

// ─────────────────────────────────────────────────────────────
// LINK PREVIEW
// ─────────────────────────────────────────────────────────────

export interface LinkPreviewData {
  url: string;
  title: string;
  description: string;
  image: string;
  hostname: string;
}

// ─────────────────────────────────────────────────────────────
// INVITATION LINK
// ─────────────────────────────────────────────────────────────

export interface InvitationLink {
  id: string;
  groupId?: string;
  channelId?: string;
  link: string;
  createdBy: string;
  createdTime: number;
  expiresTime?: number;
  maxUses?: number;
  usesCount: number;
  isRevoked: boolean;
  requiresApproval: boolean;
}

// ─────────────────────────────────────────────────────────────
// SEARCH
// ─────────────────────────────────────────────────────────────

export interface SearchUser {
  userId: string;
  username: string;
  name?: string;
  avatar: string;
  isVerified: boolean;
  lastSeen?: number;
  lastSeenStatus?: string;
  about?: string;
}

export interface GlobalSearchResult {
  users?: SearchUser[];
  groups?: Group[];
  channels?: Channel[];
  messages?: Message[];
}

// ─────────────────────────────────────────────────────────────
// NOTIFICATION SETTINGS
// ─────────────────────────────────────────────────────────────

export interface NotificationSettings {
  emailNotification: boolean;
  newMessage: boolean;
  groupMessage: boolean;
  channelPost: boolean;
  followed: boolean;
  mentioned: boolean;
}
