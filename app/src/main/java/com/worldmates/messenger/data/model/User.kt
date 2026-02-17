package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

/**
 * Полная модель пользователя с информацией о профиле
 */
data class User(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String?,
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?,
    @SerializedName("avatar") val avatar: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("about") val about: String?,
    @SerializedName("birthday") val birthday: String?,
    @SerializedName("gender") val gender: String?,
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("website") val website: String?,
    @SerializedName("working") val working: String?,
    @SerializedName("working_link") val workingLink: String?,
    @SerializedName("address") val address: String?,
    @SerializedName("country_id") val countryId: String?,
    @SerializedName("city") val city: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("zip") val zip: String?,
    @SerializedName("school") val school: String?,
    @SerializedName("language") val language: String?,
    @SerializedName("verified") val verified: Int = 0,
    @SerializedName("lastseen") val lastSeen: Long?,
    @SerializedName("lastseen_status") val lastSeenStatus: String?,
    @SerializedName("email_code") val emailCode: String?,
    @SerializedName("is_pro") val isPro: Int = 0,
    @SerializedName("pro_type") val proType: Int = 0,
    @SerializedName("joined") val joined: String?,
    @SerializedName("timezone") val timezone: String?,
    @SerializedName("referrer") val referrer: Long?,
    @SerializedName("relationship_id") val relationshipId: Int?,
    @SerializedName("follow_privacy") val followPrivacy: String?,
    @SerializedName("friend_privacy") val friendPrivacy: String?,
    @SerializedName("post_privacy") val postPrivacy: String?,
    @SerializedName("message_privacy") val messagePrivacy: String?,
    @SerializedName("confirm_followers") val confirmFollowers: String?,
    @SerializedName("show_activities_privacy") val showActivitiesPrivacy: String?,
    @SerializedName("birth_privacy") val birthPrivacy: String?,
    @SerializedName("visit_privacy") val visitPrivacy: String?,
    @SerializedName("email_notification") val emailNotification: String?,
    @SerializedName("e_liked") val eLiked: Int?,
    @SerializedName("e_wondered") val eWondered: Int?,
    @SerializedName("e_shared") val eShared: Int?,
    @SerializedName("e_followed") val eFollowed: Int?,
    @SerializedName("e_commented") val eCommented: Int?,
    @SerializedName("e_visited") val eVisited: Int?,
    @SerializedName("e_liked_page") val eLikedPage: Int?,
    @SerializedName("e_mentioned") val eMentioned: Int?,
    @SerializedName("e_joined_group") val eJoinedGroup: Int?,
    @SerializedName("e_accepted") val eAccepted: Int?,
    @SerializedName("e_profile_wall_post") val eProfileWallPost: Int?,
    @SerializedName("e_sent_gift") val eSentGift: Int?,
    @SerializedName("notification_settings") val notificationSettings: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("active") val active: String?,
    @SerializedName("admin") val admin: Int?,
    @SerializedName("balance") val balance: String?,
    @SerializedName("wallet") val wallet: String?,
    @SerializedName("followers_count") val followersCount: String?,
    @SerializedName("following_count") val followingCount: String?,
    @SerializedName("likes_count") val likesCount: String?,
    @SerializedName("groups_count") val groupsCount: String?,
    @SerializedName("details") val details: UserDetails?
)

/**
 * Дополнительные детали пользователя
 */
data class UserDetails(
    @SerializedName("post_count") val postCount: Int?,
    @SerializedName("album_count") val albumCount: Int?,
    @SerializedName("following_count") val followingCount: Int?,
    @SerializedName("followers_count") val followersCount: Int?,
    @SerializedName("groups_count") val groupsCount: Int?,
    @SerializedName("likes_count") val likesCount: Int?
)

/**
 * Настройки конфиденциальности пользователя
 */
data class UserPrivacySettings(
    val followPrivacy: String = "everyone",
    val friendPrivacy: String = "everyone",
    val postPrivacy: String = "everyone",
    val messagePrivacy: String = "everyone",
    val confirmFollowers: String = "0",
    val showActivitiesPrivacy: String = "1",
    val birthPrivacy: String = "everyone",
    val visitPrivacy: String = "everyone"
)

/**
 * Настройки уведомлений пользователя
 */
data class UserNotificationSettings(
    val emailNotification: Boolean = true,
    val eLiked: Boolean = true,
    val eWondered: Boolean = true,
    val eShared: Boolean = true,
    val eFollowed: Boolean = true,
    val eCommented: Boolean = true,
    val eVisited: Boolean = true,
    val eLikedPage: Boolean = true,
    val eMentioned: Boolean = true,
    val eJoinedGroup: Boolean = true,
    val eAccepted: Boolean = true,
    val eProfileWallPost: Boolean = true,
    val eSentGift: Boolean = true
)

/**
 * Response для получения данных пользователя
 */
data class GetUserDataResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("user_data") val userData: User?,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)

/**
 * Response для обновления данных пользователя
 */
data class UpdateUserDataResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("error_code") val errorCode: Int?,
    @SerializedName("error_message") val errorMessage: String?
)
