package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

// ==================== CHANNEL MODELS ====================

/**
 * Канал - односторонній зв'язок
 * Тільки адміни можуть постити, підписники можуть тільки читати
 */
data class Channel(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("username") val username: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String = "",
    @SerializedName("description") val description: String? = null,
    @SerializedName("subscribers_count") val subscribersCount: Int = 0,
    @SerializedName("posts_count") val postsCount: Int = 0,
    @SerializedName("owner_id") val ownerId: Long = 0,
    @SerializedName("is_private") val isPrivate: Boolean = false,
    @SerializedName("is_verified") val isVerified: Boolean = false,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("is_subscribed") val isSubscribed: Boolean = false,
    @SerializedName("created_time") val createdTime: Long = 0,
    @SerializedName("settings") val settings: ChannelSettings? = null,
    @SerializedName("category") val category: String? = null // Категорія каналу
)

/**
 * Пост у каналі (з таблиці Wo_Messages)
 */
data class ChannelPost(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("author_id") val authorId: Long = 0,
    @SerializedName("author_username") val authorUsername: String? = null,
    @SerializedName("author_name") val authorName: String? = null,
    @SerializedName("author_avatar") val authorAvatar: String? = null,
    @SerializedName("text") val text: String = "",
    @SerializedName("media") val media: List<PostMedia>? = null,
    @SerializedName("created_time") val createdTime: Long = 0,
    @SerializedName("is_edited") val isEdited: Boolean = false,
    @SerializedName("is_pinned") val isPinned: Boolean = false,
    @SerializedName("views_count") val viewsCount: Int = 0,
    @SerializedName("reactions_count") val reactionsCount: Int = 0,
    @SerializedName("comments_count") val commentsCount: Int = 0,
    @SerializedName("reactions") val reactions: List<PostReaction>? = null
)

/**
 * Медіа у пості (з таблиці Wo_Messages)
 */
data class PostMedia(
    @SerializedName("url") val url: String,
    @SerializedName("type") val type: String, // "image", "video", "audio", "file"
    @SerializedName("filename") val filename: String? = null
)

/**
 * Коментар до поста (з таблиці Wo_MessageComments)
 */
data class ChannelComment(
    @SerializedName("id") val id: Long,
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String? = null,
    @SerializedName("user_name") val userName: String? = null,
    @SerializedName("user_avatar") val userAvatar: String? = null,
    @SerializedName("text") val text: String,
    @SerializedName("time") val time: Long,
    @SerializedName("edited_time") val editedTime: Long? = null,
    @SerializedName("reply_to_comment_id") val replyToCommentId: Long? = null,
    @SerializedName("reactions_count") val reactionsCount: Int = 0
)

/**
 * Реакція на пост
 */
data class PostReaction(
    @SerializedName("emoji") val emoji: String,
    @SerializedName("count") val count: Int,
    @SerializedName("user_reacted") val userReacted: Boolean = false,
    @SerializedName("recent_users") val recentUsers: List<ReactionUser>? = null
)

/**
 * Реакція на коментар
 */
data class CommentReaction(
    @SerializedName("emoji") val emoji: String,
    @SerializedName("count") val count: Int,
    @SerializedName("user_reacted") val userReacted: Boolean = false
)

/**
 * Користувач, який поставив реакцію
 */
data class ReactionUser(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String,
    @SerializedName("avatar") val avatar: String
)

/**
 * Адміністратор каналу
 */
data class ChannelAdmin(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String,
    @SerializedName("avatar") val avatarUrl: String,
    @SerializedName("role") val role: String, // "owner", "admin", "moderator"
    @SerializedName("added_time") val addedTime: Long,
    @SerializedName("permissions") val permissions: ChannelAdminPermissions? = null
)

/**
 * Права адміністратора
 */
data class ChannelAdminPermissions(
    @SerializedName("can_post") val canPost: Boolean = true,
    @SerializedName("can_edit_posts") val canEditPosts: Boolean = true,
    @SerializedName("can_delete_posts") val canDeletePosts: Boolean = true,
    @SerializedName("can_pin_posts") val canPinPosts: Boolean = true,
    @SerializedName("can_edit_info") val canEditInfo: Boolean = false,
    @SerializedName("can_delete_channel") val canDeleteChannel: Boolean = false,
    @SerializedName("can_add_admins") val canAddAdmins: Boolean = false,
    @SerializedName("can_remove_admins") val canRemoveAdmins: Boolean = false,
    @SerializedName("can_ban_users") val canBanUsers: Boolean = false,
    @SerializedName("can_view_statistics") val canViewStatistics: Boolean = true,
    @SerializedName("can_manage_comments") val canManageComments: Boolean = true
)

/**
 * Налаштування каналу
 */
data class ChannelSettings(
    @SerializedName("allow_comments") val allowComments: Boolean = true,
    @SerializedName("allow_reactions") val allowReactions: Boolean = true,
    @SerializedName("allow_shares") val allowShares: Boolean = true,
    @SerializedName("show_statistics") val showStatistics: Boolean = true, // Показувати статистику підписникам
    @SerializedName("show_views_count") val showViewsCount: Boolean = true,
    @SerializedName("notify_subscribers_new_post") val notifySubscribersNewPost: Boolean = true,
    @SerializedName("auto_delete_posts_days") val autoDeletePostsDays: Int? = null, // Автовидалення старих постів
    @SerializedName("signature_enabled") val signatureEnabled: Boolean = false, // Підпис автора поста
    @SerializedName("comments_moderation") val commentsModeration: Boolean = false, // Модерація коментарів
    @SerializedName("allow_forwarding") val allowForwarding: Boolean = true,
    @SerializedName("slow_mode_seconds") val slowModeSeconds: Int? = null // Затримка між коментарями
)

/**
 * Статистика каналу
 */
data class ChannelStatistics(
    @SerializedName("subscribers_count") val subscribersCount: Int = 0,
    @SerializedName("posts_count") val postsCount: Int = 0,
    @SerializedName("posts_last_week") val postsLastWeek: Int = 0,
    @SerializedName("active_subscribers_24h") val activeSubscribers24h: Int = 0,
    @SerializedName("top_posts") val topPosts: List<TopPostStatistic>? = null
)

/**
 * Статистика топ-поста
 */
data class TopPostStatistic(
    @SerializedName("id") val id: Long,
    @SerializedName("text") val text: String,
    @SerializedName("views") val views: Int
)

/**
 * Підписник каналу
 */
data class ChannelSubscriber(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("user_id") val userId: Long? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("avatar") val avatarUrl: String? = null,
    @SerializedName("subscribed_time") val subscribedTime: Long? = null,
    @SerializedName("is_muted") val isMuted: Boolean = false,
    @SerializedName("is_banned") val isBanned: Boolean = false,
    @SerializedName("role") val role: String? = null,
    @SerializedName("last_seen") val lastSeen: String? = null
)

// ==================== API REQUEST MODELS ====================

data class CreateChannelRequest(
    @SerializedName("name") val name: String,
    @SerializedName("username") val username: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("is_private") val isPrivate: Boolean = false,
    @SerializedName("category") val category: String? = null
)

data class CreateChannelPostRequest(
    @SerializedName("channel_id") val channelId: Long,
    @SerializedName("text") val text: String,
    @SerializedName("media") val media: List<PostMedia>? = null,
    @SerializedName("disable_comments") val disableComments: Boolean = false,
    @SerializedName("notify_subscribers") val notifySubscribers: Boolean = true
)

data class UpdateChannelPostRequest(
    @SerializedName("post_id") val postId: Long,
    @SerializedName("text") val text: String,
    @SerializedName("media") val media: List<PostMedia>? = null
)

data class AddCommentRequest(
    @SerializedName("post_id") val postId: Long,
    @SerializedName("text") val text: String,
    @SerializedName("reply_to_id") val replyToId: Long? = null
)

data class AddReactionRequest(
    @SerializedName("target_id") val targetId: Long, // ID поста або коментаря
    @SerializedName("target_type") val targetType: String, // "post", "comment", "message"
    @SerializedName("emoji") val emoji: String
)

data class UpdateChannelSettingsRequest(
    @SerializedName("channel_id") val channelId: Long,
    @SerializedName("settings") val settings: ChannelSettings
)

data class AddChannelAdminRequest(
    @SerializedName("channel_id") val channelId: Long,
    @SerializedName("user_id") val userId: Long,
    @SerializedName("role") val role: String, // "admin", "moderator"
    @SerializedName("permissions") val permissions: ChannelAdminPermissions? = null
)

// ==================== API RESPONSE MODELS ====================

data class ChannelListResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("channels") private val _channels: List<Channel>? = null,
    @SerializedName("data") private val _data: List<Channel>? = null,
    @SerializedName("total_count") val totalCount: Int? = null,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
) {
    val channels: List<Channel>?
        get() = _channels ?: _data
}

data class ChannelDetailResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("channel") val channel: Channel?,
    @SerializedName("admins") val admins: List<ChannelAdmin>? = null,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

data class ChannelPostsResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("posts") val posts: List<ChannelPost>?,
    @SerializedName("total_count") val totalCount: Int? = null,
    @SerializedName("has_more") val hasMore: Boolean = false,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

data class ChannelCommentsResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("comments") val comments: List<ChannelComment>?,
    @SerializedName("total_count") val totalCount: Int? = null,
    @SerializedName("has_more") val hasMore: Boolean = false,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

data class CreateChannelResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("channel_id") val channelId: Long?,
    @SerializedName("channel") val channel: Channel?,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

data class CreatePostResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("post_id") val postId: Long?,
    @SerializedName("post") val post: ChannelPost?,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

data class ChannelSubscribersResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("subscribers") val subscribers: List<ChannelSubscriber>?,
    @SerializedName("total_count") val totalCount: Int? = null,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

/**
 * Забанений учасник каналу
 */
data class ChannelBannedMember(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("avatar") val avatarUrl: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("ban_time") val banTime: Long = 0,
    @SerializedName("expire_time") val expireTime: Long = 0, // 0 = permanent
    @SerializedName("banned_by") val bannedBy: Long = 0
) {
    val isPermanent: Boolean get() = expireTime == 0L
}

data class ChannelBannedMembersResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("banned_members") val bannedMembers: List<ChannelBannedMember>?,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class ChannelStatisticsResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("statistics") val statistics: ChannelStatistics?,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

// ==================== EXTENSION FUNCTIONS ====================

/**
 * Extension properties для Channel
 */
val Channel.isOwner: Boolean
    get() {
        return try {
            ownerId == com.worldmates.messenger.data.UserSession.userId
        } catch (e: Exception) {
            false
        }
    }

val Channel.canPost: Boolean
    get() = isAdmin

val Channel.canManage: Boolean
    get() = isAdmin

val Channel.lastActivity: Long
    get() = createdTime

/**
 * Конвертує Channel об'єкт в Chat об'єкт (для загального списку чатів)
 */
fun Channel.toChat(): Chat {
    return Chat(
        id = this.id,
        userId = this.ownerId,
        username = this.name,
        avatarUrl = this.avatarUrl,
        lastMessage = null, // Канали не мають lastMessage у чатах
        unreadCount = 0,
        chatType = "channel",
        isGroup = false,
        isPrivate = this.isPrivate,
        description = this.description,
        membersCount = this.subscribersCount,
        isAdmin = this.isAdmin,
        isMuted = false,
        pinnedMessageId = null,
        lastActivity = this.lastActivity
    )
}

/**
 * Extension properties для ChannelPost
 */
val ChannelPost.totalEngagement: Int
    get() = reactionsCount + commentsCount

/**
 * Стандартні емоджі для реакцій
 */
object ReactionEmojis {
    const val THUMBS_UP = "👍"
    const val HEART = "❤️"
    const val FIRE = "🔥"
    const val LAUGH = "😂"
    const val WOW = "😮"
    const val SAD = "😢"
    const val ANGRY = "😠"
    const val PARTY = "🎉"
    const val CLAP = "👏"
    const val EYES = "👀"

    val DEFAULT_REACTIONS = listOf(
        THUMBS_UP, HEART, FIRE, LAUGH, WOW, PARTY
    )

    val ALL_REACTIONS = listOf(
        THUMBS_UP, HEART, FIRE, LAUGH, WOW, SAD, ANGRY, PARTY, CLAP, EYES
    )
}
