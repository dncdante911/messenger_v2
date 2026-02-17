package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

/**
 * Модель бота WorldMates
 */
data class Bot(
    @SerializedName("bot_id") val botId: String,
    @SerializedName("username") val username: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("about") val about: String? = null,
    @SerializedName("bot_type") val botType: String = "standard",
    @SerializedName("status") val status: String = "active",
    @SerializedName("is_public") val isPublic: Int = 1,
    @SerializedName("is_inline") val isInline: Int = 0,
    @SerializedName("can_join_groups") val canJoinGroups: Int = 1,
    @SerializedName("supports_commands") val supportsCommands: Int = 1,
    @SerializedName("category") val category: String? = null,
    @SerializedName("tags") val tags: String? = null,
    @SerializedName("total_users") val totalUsers: Int = 0,
    @SerializedName("active_users_24h") val activeUsers24h: Int = 0,
    @SerializedName("messages_sent") val messagesSent: Long = 0,
    @SerializedName("messages_received") val messagesReceived: Long = 0,
    @SerializedName("commands_count") val commandsCount: Int = 0,
    @SerializedName("commands") val commands: List<BotCommand>? = null,
    @SerializedName("webhook_url") val webhookUrl: String? = null,
    @SerializedName("webhook_enabled") val webhookEnabled: Int = 0,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("last_active_at") val lastActiveAt: String? = null,
    // Only returned on creation
    @SerializedName("bot_token") val botToken: String? = null
) {
    val isVerified: Boolean get() = botType == "verified"
    val isSystem: Boolean get() = botType == "system"
    val isActive: Boolean get() = status == "active"
}

/**
 * Команда бота (slash command)
 */
data class BotCommand(
    @SerializedName("command") val command: String,
    @SerializedName("description") val description: String,
    @SerializedName("usage_hint") val usageHint: String? = null,
    @SerializedName("scope") val scope: String = "all"
)

/**
 * Повідомлення від/до бота
 */
data class BotMessage(
    @SerializedName("message_id") val messageId: Long,
    @SerializedName("real_message_id") val realMessageId: Long? = null,
    @SerializedName("chat_id") val chatId: String,
    @SerializedName("text") val text: String? = null,
    @SerializedName("date") val date: Long = 0,
    @SerializedName("media") val media: BotMedia? = null,
    @SerializedName("reply_markup") val replyMarkup: BotReplyMarkup? = null
)

/**
 * Медіа в повідомленні бота
 */
data class BotMedia(
    @SerializedName("type") val type: String, // image, video, audio, file
    @SerializedName("url") val url: String
)

/**
 * Клавіатура (inline buttons) бота
 */
data class BotReplyMarkup(
    @SerializedName("inline_keyboard") val inlineKeyboard: List<List<BotInlineButton>>? = null,
    @SerializedName("keyboard") val keyboard: List<List<BotKeyboardButton>>? = null,
    @SerializedName("resize_keyboard") val resizeKeyboard: Boolean = true,
    @SerializedName("one_time_keyboard") val oneTimeKeyboard: Boolean = false
)

/**
 * Inline кнопка (callback або URL)
 */
data class BotInlineButton(
    @SerializedName("text") val text: String,
    @SerializedName("callback_data") val callbackData: String? = null,
    @SerializedName("url") val url: String? = null
)

/**
 * Кнопка reply клавіатури
 */
data class BotKeyboardButton(
    @SerializedName("text") val text: String,
    @SerializedName("request_contact") val requestContact: Boolean = false,
    @SerializedName("request_location") val requestLocation: Boolean = false
)

/**
 * Опитування від бота
 */
data class BotPoll(
    @SerializedName("poll_id") val pollId: Int,
    @SerializedName("message_id") val messageId: Long? = null,
    @SerializedName("question") val question: String,
    @SerializedName("options") val options: List<String>,
    @SerializedName("type") val type: String = "regular",
    @SerializedName("is_anonymous") val isAnonymous: Int = 1,
    @SerializedName("total_voters") val totalVoters: Int = 0,
    @SerializedName("is_closed") val isClosed: Boolean = false
)

/**
 * Результат опитування (при закритті)
 */
data class BotPollResult(
    @SerializedName("option_text") val optionText: String,
    @SerializedName("voter_count") val voterCount: Int = 0,
    @SerializedName("option_index") val optionIndex: Int = 0
)

/**
 * Оновлення від бота (для long polling / webhook)
 */
data class BotUpdate(
    @SerializedName("update_id") val updateId: Long,
    @SerializedName("message") val message: BotUpdateMessage? = null,
    @SerializedName("command") val command: BotUpdateCommand? = null,
    @SerializedName("callback_query") val callbackQuery: BotCallbackQuery? = null
)

data class BotUpdateMessage(
    @SerializedName("message_id") val messageId: Long,
    @SerializedName("from") val from: BotUpdateUser,
    @SerializedName("chat") val chat: BotUpdateChat,
    @SerializedName("date") val date: Long,
    @SerializedName("text") val text: String? = null,
    @SerializedName("media") val media: BotMedia? = null,
    @SerializedName("entities") val entities: List<BotMessageEntity>? = null
)

data class BotUpdateUser(
    @SerializedName("id") val id: Long,
    @SerializedName("username") val username: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("avatar") val avatar: String? = null
)

data class BotUpdateChat(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String = "private"
)

data class BotUpdateCommand(
    @SerializedName("name") val name: String,
    @SerializedName("args") val args: String? = null
)

data class BotCallbackQuery(
    @SerializedName("id") val id: String,
    @SerializedName("from") val from: BotUpdateUser,
    @SerializedName("data") val data: String,
    @SerializedName("message") val message: BotUpdateMessage? = null
)

data class BotMessageEntity(
    @SerializedName("type") val type: String, // bot_command, bold, italic, code, text_link
    @SerializedName("offset") val offset: Int,
    @SerializedName("length") val length: Int,
    @SerializedName("url") val url: String? = null
)

// ==================== API RESPONSES ====================

data class CreateBotResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("bot") val bot: Bot? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class BotListResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("bots") val bots: List<Bot>? = null,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class BotInfoResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("bot") val bot: Bot? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class BotSearchResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("bots") val bots: List<Bot>? = null,
    @SerializedName("categories") val categories: List<BotCategory>? = null,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class BotCategory(
    @SerializedName("category") val category: String,
    @SerializedName("count") val count: Int
)

data class BotCommandsResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("commands") val commands: List<BotCommand>? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class BotSendMessageResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message_id") val messageId: Long? = null,
    @SerializedName("real_message_id") val realMessageId: Long? = null,
    @SerializedName("chat_id") val chatId: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("date") val date: Long = 0,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class BotUpdatesResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("updates") val updates: List<BotUpdate>? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class BotPollResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("poll") val poll: BotPoll? = null,
    @SerializedName("results") val results: List<BotPollResult>? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class BotTokenResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("bot_token") val botToken: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class BotWebhookInfoResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("url") val url: String? = null,
    @SerializedName("has_custom_certificate") val hasCustomCertificate: Boolean = false,
    @SerializedName("pending_update_count") val pendingUpdateCount: Int = 0,
    @SerializedName("max_connections") val maxConnections: Int = 40,
    @SerializedName("allowed_updates") val allowedUpdates: List<String>? = null,
    @SerializedName("last_error_date") val lastErrorDate: Long? = null,
    @SerializedName("last_error_message") val lastErrorMessage: String? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class BotGenericResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)
