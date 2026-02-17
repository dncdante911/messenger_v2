package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

// ==================== STORY MODELS ====================

/**
 * Story - короткий відео/фото контент на 24-48 годин
 */
data class Story(
    @SerializedName("id") val id: Long,
    @SerializedName("user_id") val userId: Long,
    @SerializedName("page_id") val pageId: Long? = null, // ID канала (null = личная story)
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("posted") val posted: Long, // Unix timestamp
    @SerializedName("expire") val expire: Long, // Unix timestamp
    @SerializedName("thumbnail") val thumbnail: String,
    @SerializedName("user_data") val userData: StoryUser? = null,
    @SerializedName("channel_data") val channelData: ChannelStoryData? = null,
    @SerializedName("thumb") val thumb: StoryMedia? = null,
    @SerializedName("images") val images: List<StoryMedia>? = null,
    @SerializedName("videos") val videos: List<StoryMedia>? = null,
    @SerializedName("mediaItems") val apiMediaItems: List<StoryMedia>? = null, // NEW: Support for mediaItems from API
    @SerializedName("is_owner") val isOwner: Boolean = false,
    @SerializedName("is_viewed") val isViewed: Int = 0,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("comment_count") val commentCount: Int = 0,
    @SerializedName("reaction") val reaction: StoryReactions? = null
) {
    /**
     * Чи активна ще story (не протермінована)
     */
    fun isExpired(): Boolean {
        return System.currentTimeMillis() / 1000 > expire
    }

    /**
     * Скільки часу залишилось до видалення (в секундах)
     */
    fun getTimeLeft(): Long {
        return expire - (System.currentTimeMillis() / 1000)
    }

    /**
     * Відсоток прогресу перегляду (0-100)
     */
    fun getProgressPercentage(): Int {
        val totalTime = expire - posted
        val elapsed = (System.currentTimeMillis() / 1000) - posted
        return ((elapsed.toFloat() / totalTime.toFloat()) * 100).toInt().coerceIn(0, 100)
    }

    // ========== Helper properties for UI ==========

    /**
     * Всі медіа файли (об'єднані images + videos)
     * Використовує apiMediaItems якщо доступно, інакше комбінує images + videos
     */
    val mediaItems: List<StoryMedia>
        get() = apiMediaItems ?: ((images ?: emptyList()) + (videos ?: emptyList()))

    /**
     * Чи переглянута story
     */
    val seen: Boolean
        get() = isViewed == 1

    /**
     * Кількість переглядів
     */
    val viewsCount: Int
        get() = viewCount

    /**
     * Кількість коментарів
     */
    val commentsCount: Int
        get() = commentCount

    /**
     * Реакції на story
     */
    val reactions: StoryReactions
        get() = reaction ?: StoryReactions()

    /**
     * Час публікації (для сумісності)
     */
    val time: Long
        get() = posted

    /**
     * Чи є це story каналу
     */
    val isChannelStory: Boolean
        get() = pageId != null && pageId > 0
}

/**
 * Дані каналу для channel story
 */
data class ChannelStoryData(
    @SerializedName("group_id") val groupId: Long,
    @SerializedName("group_name") val groupName: String,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("owner_id") val ownerId: Long = 0
)

/**
 * Медіа файл story (фото або відео)
 */
data class StoryMedia(
    @SerializedName("id") val id: Long,
    @SerializedName("story_id") val storyId: Long,
    @SerializedName("type") val type: String, // "image" або "video"
    @SerializedName("filename") val filename: String,
    @SerializedName("expire") val expire: Long? = null,
    @SerializedName("duration") val duration: Int = 0 // Тривалість відео в секундах
) {
    fun isVideo(): Boolean = type == "video"
    fun isImage(): Boolean = type == "image"
}

/**
 * Користувач, який опублікував story
 */
data class StoryUser(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("avatar_org") val avatarOrg: String? = null,
    @SerializedName("is_pro") val isPro: Int = 0,
    @SerializedName("verified") val verified: Int = 0
) {
    fun getFullName(): String {
        return when {
            !firstName.isNullOrBlank() && !lastName.isNullOrBlank() -> "$firstName $lastName"
            !firstName.isNullOrBlank() -> firstName
            else -> username
        }
    }

    fun isProUser(): Boolean = isPro == 1
    fun isVerified(): Boolean = verified == 1

    // Helper property для UI
    val name: String
        get() = getFullName()
}

/**
 * Коментар до story
 */
data class StoryComment(
    @SerializedName("id") val id: Long,
    @SerializedName("story_id") val storyId: Long,
    @SerializedName("user_id") val userId: Long,
    @SerializedName("text") val text: String,
    @SerializedName("time") val time: Long, // Unix timestamp
    @SerializedName("user_data") val userData: StoryUser? = null,
    @SerializedName("offset_id") val offsetId: Long? = null
) {
    /**
     * Форматований час коментаря
     */
    fun getFormattedTime(): String {
        val now = System.currentTimeMillis() / 1000
        val diff = now - time

        return when {
            diff < 60 -> "щойно"
            diff < 3600 -> "${diff / 60} хв тому"
            diff < 86400 -> "${diff / 3600} год тому"
            diff < 604800 -> "${diff / 86400} дн тому"
            else -> "${diff / 604800} тиж тому"
        }
    }
}

/**
 * Реакції на story
 */
data class StoryReactions(
    @SerializedName("like") val like: Int = 0,
    @SerializedName("love") val love: Int = 0,
    @SerializedName("haha") val haha: Int = 0,
    @SerializedName("wow") val wow: Int = 0,
    @SerializedName("sad") val sad: Int = 0,
    @SerializedName("angry") val angry: Int = 0,
    @SerializedName("is_reacted") val isReacted: Boolean = false,
    @SerializedName("type") val type: String? = null
) {
    fun getTotalReactions(): Int {
        return like + love + haha + wow + sad + angry
    }

    // Helper property для UI
    val total: Int
        get() = getTotalReactions()
}

/**
 * Користувач, який переглянув story
 */
data class StoryViewer(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("time") val time: Long = System.currentTimeMillis() / 1000, // Unix timestamp
    @SerializedName("offset_id") val offsetId: Long? = null
) {
    val name: String
        get() = when {
            !firstName.isNullOrBlank() && !lastName.isNullOrBlank() -> "$firstName $lastName"
            !firstName.isNullOrBlank() -> firstName
            else -> username
        }
}

/**
 * Обмеження для Stories залежно від типу підписки
 */
data class StoryLimits(
    val maxStories: Int,
    val maxVideoDuration: Int, // в секундах
    val expireHours: Int,
    val canComment: Boolean = true,
    val canReact: Boolean = true
) {
    companion object {
        fun forFreeUser(): StoryLimits {
            return StoryLimits(
                maxStories = 5,
                maxVideoDuration = 30,
                expireHours = 24
            )
        }

        fun forProUser(): StoryLimits {
            return StoryLimits(
                maxStories = 25,
                maxVideoDuration = 60,
                expireHours = 48
            )
        }

        fun forUser(isPro: Boolean): StoryLimits {
            return if (isPro) forProUser() else forFreeUser()
        }
    }
}

// ==================== API RESPONSE MODELS ====================

/**
 * Відповідь на створення story
 */
data class CreateStoryResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("story_id") val storyId: Long? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/**
 * Відповідь на отримання списку stories
 */
data class GetStoriesResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("stories") val stories: List<Story>? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/**
 * Відповідь на отримання story за ID
 */
data class GetStoryByIdResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("story") val story: Story? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/**
 * Відповідь на видалення story
 */
data class DeleteStoryResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/**
 * Відповідь на отримання переглядів story
 */
data class GetStoryViewsResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("users") val users: List<StoryViewer>? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/**
 * Відповідь на реакцію на story
 */
data class ReactStoryResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/**
 * Відповідь на приглушення story
 */
data class MuteStoryResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/**
 * Відповідь на створення коментаря
 */
data class CreateStoryCommentResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("comment") val comment: StoryComment? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/**
 * Відповідь на отримання коментарів
 */
data class GetStoryCommentsResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("comments") val comments: List<StoryComment>? = null,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/**
 * Відповідь на видалення коментаря
 */
data class DeleteStoryCommentResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)