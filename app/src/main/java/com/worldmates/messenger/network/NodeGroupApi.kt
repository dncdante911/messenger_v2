package com.worldmates.messenger.network

import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

/**
 * Node.js REST API — Retrofit interface for group chats.
 *
 * All group operations route through Node.js (port 449) instead of PHP.
 * Auth: access-token header (set automatically by NodeRetrofitClient).
 */
interface NodeGroupApi {

    // ═══════════════════════ GROUP MANAGEMENT ═════════════════════════════════

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_LIST)
    suspend fun getGroups(
        @Field("limit")  limit: Int = 50,
        @Field("offset") offset: Int = 0
    ): GroupListResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_DETAILS)
    suspend fun getGroupDetails(
        @Field("group_id") groupId: Long
    ): GroupDetailResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_CREATE)
    suspend fun createGroup(
        @Field("group_name")  name: String,
        @Field("description") description: String? = null,
        @Field("is_private")  isPrivate: Int = 0,
        @Field("parts")       parts: String? = null,   // comma-separated user IDs
        @Field("user_ids")    userIds: String? = null  // alternative to parts
    ): CreateGroupResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_UPDATE)
    suspend fun updateGroup(
        @Field("group_id")    groupId: Long,
        @Field("group_name")  name: String? = null,
        @Field("description") description: String? = null,
        @Field("is_private")  isPrivate: Int? = null
    ): CreateGroupResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_DELETE)
    suspend fun deleteGroup(
        @Field("group_id") groupId: Long
    ): GroupSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_LEAVE)
    suspend fun leaveGroup(
        @Field("group_id") groupId: Long
    ): GroupSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_SEARCH)
    suspend fun searchGroups(
        @Field("query")  query: String,
        @Field("limit")  limit: Int = 30,
        @Field("offset") offset: Int = 0
    ): GroupListResponse

    // ═══════════════════════ MEMBERS ══════════════════════════════════════════

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_MEMBERS)
    suspend fun getGroupMembers(
        @Field("group_id") groupId: Long,
        @Field("limit")    limit: Int = 100,
        @Field("offset")   offset: Int = 0
    ): GroupMembersResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_ADD_MEMBER)
    suspend fun addGroupMember(
        @Field("group_id") groupId: Long,
        @Field("user_id")  userId: Long? = null,
        @Field("parts")    parts: String? = null  // comma-separated user IDs
    ): GroupSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_REMOVE_MEMBER)
    suspend fun removeGroupMember(
        @Field("group_id") groupId: Long,
        @Field("user_id")  userId: Long
    ): GroupSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_SET_ROLE)
    suspend fun setGroupRole(
        @Field("group_id") groupId: Long,
        @Field("user_id")  userId: Long,
        @Field("role")     role: String  // "admin", "moderator", "member"
    ): GroupSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_JOIN)
    suspend fun joinGroup(
        @Field("group_id") groupId: Long
    ): GroupDetailResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_REQUEST_JOIN)
    suspend fun requestJoinGroup(
        @Field("group_id") groupId: Long
    ): GroupSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_JOIN_REQUESTS)
    suspend fun getJoinRequests(
        @Field("group_id") groupId: Long
    ): GroupJoinRequestsResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_APPROVE_JOIN)
    suspend fun approveJoinRequest(
        @Field("group_id") groupId: Long,
        @Field("user_id")  userId: Long
    ): GroupSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_REJECT_JOIN)
    suspend fun rejectJoinRequest(
        @Field("group_id") groupId: Long,
        @Field("user_id")  userId: Long
    ): GroupSimpleResponse

    // ═══════════════════════ MESSAGES ══════════════════════════════════════════

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_MESSAGES_GET)
    suspend fun getGroupMessages(
        @Field("group_id")          groupId: Long,
        @Field("limit")             limit: Int = 30,
        @Field("after_message_id")  afterMessageId: Long = 0,
        @Field("before_message_id") beforeMessageId: Long = 0
    ): GroupMessageListResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_MESSAGES_SEND)
    suspend fun sendGroupMessage(
        @Field("group_id") groupId: Long,
        @Field("text")     text: String,
        @Field("reply_id") replyId: Long = 0,
        @Field("stickers") stickers: String? = null
    ): GroupMessageResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_MESSAGES_LOADMORE)
    suspend fun loadMoreGroupMessages(
        @Field("group_id")          groupId: Long,
        @Field("before_message_id") beforeMessageId: Long,
        @Field("limit")             limit: Int = 15
    ): GroupMessageListResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_MESSAGES_EDIT)
    suspend fun editGroupMessage(
        @Field("message_id") messageId: Long,
        @Field("text")       text: String
    ): GroupSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_MESSAGES_DELETE)
    suspend fun deleteGroupMessage(
        @Field("message_id") messageId: Long
    ): GroupSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_MESSAGES_PIN)
    suspend fun pinGroupMessage(
        @Field("group_id")   groupId: Long,
        @Field("message_id") messageId: Long
    ): GroupMessageResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_MESSAGES_UNPIN)
    suspend fun unpinGroupMessage(
        @Field("group_id") groupId: Long
    ): GroupSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_MESSAGES_SEARCH)
    suspend fun searchGroupMessages(
        @Field("group_id") groupId: Long,
        @Field("query")    query: String,
        @Field("limit")    limit: Int = 50,
        @Field("offset")   offset: Int = 0
    ): GroupMessageListResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_MESSAGES_SEEN)
    suspend fun markGroupMessagesSeen(
        @Field("group_id") groupId: Long
    ): GroupSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_MESSAGES_TYPING)
    suspend fun sendGroupTyping(
        @Field("group_id") groupId: Long,
        @Field("typing")   typing: Boolean = true
    ): GroupSimpleResponse

    // ═══════════════════════ ADMIN ══════════════════════════════════════════════

    @Multipart
    @POST(Constants.NODE_GROUP_UPLOAD_AVATAR)
    suspend fun uploadGroupAvatar(
        @Part("group_id") groupId: RequestBody,
        @Part avatar: MultipartBody.Part
    ): GroupAvatarResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_SETTINGS)
    suspend fun updateGroupSettings(
        @Field("group_id")               groupId: Long,
        @Field("group_name")             groupName: String? = null,
        @Field("description")            description: String? = null,
        @Field("is_private")             isPrivate: Int? = null,
        @Field("who_can_send_messages")  whoCanSendMessages: String? = null,
        @Field("formatting_permissions") formattingPermissions: String? = null,
        @Field("join_requests_enabled")  joinRequestsEnabled: Boolean? = null
    ): GroupDetailResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_MUTE)
    suspend fun muteGroup(
        @Field("group_id") groupId: Long
    ): GroupSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_UNMUTE)
    suspend fun unmuteGroup(
        @Field("group_id") groupId: Long
    ): GroupSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_QR_GENERATE)
    suspend fun generateGroupQr(
        @Field("group_id") groupId: Long
    ): GroupQrResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_QR_JOIN)
    suspend fun joinGroupByQr(
        @Field("invite_code") inviteCode: String
    ): GroupDetailResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_STATISTICS)
    suspend fun getGroupStatistics(
        @Field("group_id") groupId: Long
    ): GroupStatisticsResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_ADD_ADMIN)
    suspend fun addGroupAdmin(
        @Field("group_id") groupId: Long,
        @Field("user_id")  userId: Long
    ): GroupSimpleResponse

    @FormUrlEncoded
    @POST(Constants.NODE_GROUP_REMOVE_ADMIN)
    suspend fun removeGroupAdmin(
        @Field("group_id") groupId: Long,
        @Field("user_id")  userId: Long
    ): GroupSimpleResponse
}
