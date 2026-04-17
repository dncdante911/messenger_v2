package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

data class ChannelReply(
    @SerializedName("id")                    val id: Long = 0,
    @SerializedName("post_id")               val postId: Long = 0,
    @SerializedName("channel_id")            val channelId: Long = 0,
    @SerializedName("channel_name")          val channelName: String = "",
    @SerializedName("channel_avatar")        val channelAvatar: String = "",
    @SerializedName("post_text")             val postText: String = "",
    @SerializedName("original_comment_id")   val originalCommentId: Long = 0,
    @SerializedName("original_comment_text") val originalCommentText: String = "",
    @SerializedName("sender_user_id")        val senderUserId: Long = 0,
    @SerializedName("sender_username")       val senderUsername: String = "",
    @SerializedName("sender_name")           val senderName: String = "",
    @SerializedName("sender_avatar")         val senderAvatar: String = "",
    @SerializedName("text")                  val text: String = "",
    @SerializedName("time")                  val time: Long = 0
)

data class ChannelReplyInboxResponse(
    @SerializedName("api_status")    val apiStatus: Int = 0,
    @SerializedName("replies")       val replies: List<ChannelReply>? = null,
    @SerializedName("total")         val total: Int = 0,
    @SerializedName("error_message") val errorMessage: String? = null
)
