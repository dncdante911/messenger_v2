package com.worldmates.messenger.network

import com.google.gson.annotations.SerializedName
import com.worldmates.messenger.data.model.Channel

// ==================== UPLOAD ====================
// Used by MediaUploader, NodeApi

data class XhrUploadResponse(
    @SerializedName("status") val status: Int,
    @SerializedName("image") val imageUrl: String?,
    @SerializedName("image_src") val imageSrc: String?,
    @SerializedName("video") val videoUrl: String?,
    @SerializedName("video_src") val videoSrc: String?,
    @SerializedName("audio") val audioUrl: String?,
    @SerializedName("audio_src") val audioSrc: String?,
    @SerializedName("file") val fileUrl: String?,
    @SerializedName("file_src") val fileSrc: String?,
    @SerializedName("error") val error: String?,
    @SerializedName("length") val length: Int? = null
)

// ==================== CHANNEL SUBSCRIBE ====================
// Used by NodeChannelApi, ChannelsViewModel

data class SubscribeChannelResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("channel") val channel: Channel? = null
)

// ==================== POLLS ====================
// Used by NodeChannelApi, NodeGroupApi, PollMessageComponent, MessageBubbleComponents, Channel

data class PollOption(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("text") val text: String = "",
    @SerializedName("vote_count") val voteCount: Int = 0,
    @SerializedName("percent") val percent: Int = 0,
    @SerializedName("is_voted") val isVoted: Boolean = false
)

data class Poll(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("question") val question: String = "",
    @SerializedName("poll_type") val pollType: String = "regular",
    @SerializedName("is_anonymous") val isAnonymous: Boolean = true,
    @SerializedName("allows_multiple_answers") val allowsMultipleAnswers: Boolean = false,
    @SerializedName("is_closed") val isClosed: Boolean = false,
    @SerializedName("total_votes") val totalVotes: Int = 0,
    @SerializedName("created_by") val createdBy: Long = 0,
    @SerializedName("options") val options: List<PollOption> = emptyList()
)

data class PollResponse(
    @SerializedName("api_status") val apiStatus: Int = 0,
    @SerializedName("poll") val poll: Poll? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

// ==================== GROUP CUSTOMIZATION ====================
// Used by NodeGroupApi, GroupThemeManager

data class GroupCustomizationData(
    @SerializedName("group_id") val groupId: Long,
    @SerializedName("bubble_style") val bubbleStyle: String = "STANDARD",
    @SerializedName("preset_background") val presetBackground: String = "ocean",
    @SerializedName("accent_color") val accentColor: String = "#2196F3",
    @SerializedName("enabled_by_admin") val enabledByAdmin: Boolean = true,
    @SerializedName("updated_at") val updatedAt: Long = 0,
    @SerializedName("updated_by") val updatedBy: Long = 0
)

data class GroupCustomizationResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("customization") val customization: GroupCustomizationData? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

// ==================== SEARCH ====================
// Used by GroupsViewModel, CreateGroupDialog, InviteMembersUI

data class SearchUser(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String,
    @SerializedName("name") val name: String?,
    @SerializedName("avatar") val avatarUrl: String,
    @SerializedName("verified") val verified: Int = 0,
    @SerializedName("lastseen") val lastSeen: Long?,
    @SerializedName("lastseen_status") val lastSeenStatus: String?,
    @SerializedName("about") val about: String?
)
