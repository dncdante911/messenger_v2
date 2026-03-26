package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

// ─── Business Profile ─────────────────────────────────────────────────────────

data class BusinessProfile(
    @SerializedName("id")                    val id: Long = 0,
    @SerializedName("user_id")               val userId: Long = 0,
    @SerializedName("business_name")         val businessName: String? = null,
    @SerializedName("category")              val category: String? = null,
    @SerializedName("description")           val description: String? = null,
    @SerializedName("address")               val address: String? = null,
    @SerializedName("lat")                   val lat: Double? = null,
    @SerializedName("lng")                   val lng: Double? = null,
    @SerializedName("phone")                 val phone: String? = null,
    @SerializedName("email")                 val email: String? = null,
    @SerializedName("website")               val website: String? = null,
    @SerializedName("bio_link")              val bioLink: String? = null,
    @SerializedName("auto_reply_enabled")    val autoReplyEnabled: Int = 0,
    @SerializedName("auto_reply_text")       val autoReplyText: String? = null,
    @SerializedName("auto_reply_mode")       val autoReplyMode: String = "always",
    @SerializedName("greeting_enabled")      val greetingEnabled: Int = 0,
    @SerializedName("greeting_text")         val greetingText: String? = null,
    @SerializedName("away_enabled")          val awayEnabled: Int = 0,
    @SerializedName("away_text")             val awayText: String? = null,
    @SerializedName("badge_enabled")         val badgeEnabled: Int = 1,
    @SerializedName("verification_status")   val verificationStatus: String = "none",
    @SerializedName("verification_note")     val verificationNote: String? = null,
    @SerializedName("created_at")            val createdAt: Long = 0,
    @SerializedName("updated_at")            val updatedAt: Long = 0
)

data class BusinessHour(
    @SerializedName("id")         val id: Long = 0,
    @SerializedName("user_id")    val userId: Long = 0,
    @SerializedName("weekday")    val weekday: Int = 0,   // 0=Sun … 6=Sat
    @SerializedName("is_open")    val isOpen: Int = 1,
    @SerializedName("open_time")  val openTime: String = "09:00",
    @SerializedName("close_time") val closeTime: String = "18:00"
)

data class BusinessQuickReply(
    @SerializedName("id")         val id: Long = 0,
    @SerializedName("user_id")    val userId: Long = 0,
    @SerializedName("shortcut")   val shortcut: String = "",
    @SerializedName("text")       val text: String = "",
    @SerializedName("media_url")  val mediaUrl: String? = null,
    @SerializedName("created_at") val createdAt: Long = 0,
    @SerializedName("updated_at") val updatedAt: Long = 0
)

data class BusinessLink(
    @SerializedName("id")             val id: Long = 0,
    @SerializedName("user_id")        val userId: Long = 0,
    @SerializedName("title")          val title: String = "",
    @SerializedName("prefilled_text") val prefilledText: String? = null,
    @SerializedName("slug")           val slug: String = "",
    @SerializedName("views")          val views: Int = 0,
    @SerializedName("url")            val url: String = "",
    @SerializedName("created_at")     val createdAt: Long = 0
)

// ─── API Responses ────────────────────────────────────────────────────────────

data class BusinessProfileResponse(
    @SerializedName("api_status")     val apiStatus: Int,
    @SerializedName("profile")        val profile: BusinessProfile?,
    @SerializedName("hours")          val hours: List<BusinessHour>?,
    @SerializedName("error_message")  val errorMessage: String? = null
)

data class BusinessHoursResponse(
    @SerializedName("api_status")     val apiStatus: Int,
    @SerializedName("hours")          val hours: List<BusinessHour>?,
    @SerializedName("error_message")  val errorMessage: String? = null
)

data class BusinessQuickRepliesResponse(
    @SerializedName("api_status")        val apiStatus: Int,
    @SerializedName("quick_replies")     val quickReplies: List<BusinessQuickReply>?,
    @SerializedName("quick_reply")       val quickReply: BusinessQuickReply? = null,
    @SerializedName("error_message")     val errorMessage: String? = null
)

data class BusinessLinksResponse(
    @SerializedName("api_status")    val apiStatus: Int,
    @SerializedName("links")         val links: List<BusinessLink>?,
    @SerializedName("link")          val link: BusinessLink? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class BusinessActionResponse(
    @SerializedName("api_status")    val apiStatus: Int,
    @SerializedName("error_message") val errorMessage: String? = null
)

// ─── Request bodies ───────────────────────────────────────────────────────────

data class UpdateBusinessProfileRequest(
    @SerializedName("business_name")      val businessName: String? = null,
    @SerializedName("category")           val category: String? = null,
    @SerializedName("description")        val description: String? = null,
    @SerializedName("address")            val address: String? = null,
    @SerializedName("lat")                val lat: Double? = null,
    @SerializedName("lng")                val lng: Double? = null,
    @SerializedName("phone")              val phone: String? = null,
    @SerializedName("email")              val email: String? = null,
    @SerializedName("website")            val website: String? = null,
    @SerializedName("bio_link")           val bioLink: String? = null,
    @SerializedName("auto_reply_enabled") val autoReplyEnabled: Boolean = false,
    @SerializedName("auto_reply_text")    val autoReplyText: String? = null,
    @SerializedName("auto_reply_mode")    val autoReplyMode: String = "always",
    @SerializedName("greeting_enabled")   val greetingEnabled: Boolean = false,
    @SerializedName("greeting_text")      val greetingText: String? = null,
    @SerializedName("away_enabled")       val awayEnabled: Boolean = false,
    @SerializedName("away_text")          val awayText: String? = null,
    @SerializedName("badge_enabled")      val badgeEnabled: Boolean = true
)

// ─── Creator / Business Extended Models ───────────────────────────────────────

data class BusinessStatDay(
    @SerializedName("date")              val date: String = "",
    @SerializedName("profile_views")     val profileViews: Int = 0,
    @SerializedName("messages_received") val messagesReceived: Int = 0,
    @SerializedName("link_clicks")       val linkClicks: Int = 0,
)

data class BusinessStatTotals(
    @SerializedName("profile_views")     val profileViews: Int = 0,
    @SerializedName("messages_received") val messagesReceived: Int = 0,
    @SerializedName("link_clicks")       val linkClicks: Int = 0,
)

data class BusinessStatsResponse(
    @SerializedName("api_status")    val apiStatus: Int = 0,
    @SerializedName("days")          val days: Int = 30,
    @SerializedName("totals")        val totals: BusinessStatTotals = BusinessStatTotals(),
    @SerializedName("daily")         val daily: List<BusinessStatDay> = emptyList(),
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class BusinessVerificationResponse(
    @SerializedName("api_status")          val apiStatus: Int = 0,
    @SerializedName("verification_status") val verificationStatus: String = "none",
    @SerializedName("message")            val message: String? = null,
    @SerializedName("error_message")      val errorMessage: String? = null,
)

data class BusinessApiKey(
    @SerializedName("api_key")      val apiKey: String = "",
    @SerializedName("label")        val label: String = "My API Key",
    @SerializedName("last_used_at") val lastUsedAt: String? = null,
)

data class BusinessApiKeyResponse(
    @SerializedName("api_status")    val apiStatus: Int = 0,
    @SerializedName("api_key")       val apiKey: BusinessApiKey? = null,
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class ChannelScheduledPost(
    @SerializedName("id")           val id: Long = 0,
    @SerializedName("channel_id")   val channelId: Long = 0,
    @SerializedName("author_id")    val authorId: Long = 0,
    @SerializedName("text")         val text: String? = null,
    @SerializedName("media_url")    val mediaUrl: String? = null,
    @SerializedName("media_type")   val mediaType: String? = null,
    @SerializedName("scheduled_at") val scheduledAt: String = "",
    @SerializedName("is_pinned")    val isPinned: Int = 0,
    @SerializedName("status")       val status: String = "pending",
    @SerializedName("author_name")  val authorName: String? = null,
)

data class ChannelScheduledPostsResponse(
    @SerializedName("api_status")       val apiStatus: Int = 0,
    @SerializedName("scheduled_posts")  val scheduledPosts: List<ChannelScheduledPost> = emptyList(),
    @SerializedName("error_message")    val errorMessage: String? = null,
)

data class ChannelScheduledPostResponse(
    @SerializedName("api_status")    val apiStatus: Int = 0,
    @SerializedName("scheduled_post") val scheduledPost: ChannelScheduledPost? = null,
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class CreateScheduledPostRequest(
    @SerializedName("text")         val text: String? = null,
    @SerializedName("media_url")    val mediaUrl: String? = null,
    @SerializedName("media_type")   val mediaType: String? = null,
    @SerializedName("scheduled_at") val scheduledAt: String = "",
    @SerializedName("is_pinned")    val isPinned: Boolean = false,
)

data class UpdateBusinessHoursRequest(
    @SerializedName("hours") val hours: List<BusinessHourRequest>
)

data class BusinessHourRequest(
    @SerializedName("weekday")    val weekday: Int,
    @SerializedName("is_open")    val isOpen: Boolean,
    @SerializedName("open_time")  val openTime: String,
    @SerializedName("close_time") val closeTime: String
)

data class CreateQuickReplyRequest(
    @SerializedName("shortcut")  val shortcut: String,
    @SerializedName("text")      val text: String,
    @SerializedName("media_url") val mediaUrl: String? = null
)

data class CreateBusinessLinkRequest(
    @SerializedName("title")          val title: String,
    @SerializedName("prefilled_text") val prefilledText: String? = null
)
