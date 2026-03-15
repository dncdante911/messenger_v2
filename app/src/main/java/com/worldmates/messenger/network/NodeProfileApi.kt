package com.worldmates.messenger.network

import com.google.gson.annotations.SerializedName
import com.worldmates.messenger.data.model.BlockedUser
import com.worldmates.messenger.data.model.GetUserDataResponse
import com.worldmates.messenger.data.model.GetUserRatingResponse
import com.worldmates.messenger.data.model.RateUserResponse
import com.worldmates.messenger.data.model.UpdateUserDataResponse
import com.worldmates.messenger.data.model.User
import retrofit2.http.*

/**
 * Node.js Profile API — замінює PHP ?type=get-user-data / update-user-data / get_user_rating / rate_user.
 *
 * Auth: автоматично додається через [NodeRetrofitClient] (заголовок access-token).
 *
 * Endpoint mapping:
 *   PHP  GET  ?type=get-user-data        → Node GET  api/node/users/me  або api/node/users/{id}
 *   PHP  POST ?type=update-user-data     → Node PUT  api/node/users/me
 *   PHP  POST ?type=get_user_rating      → Node GET  api/node/users/{id}/rating
 *   PHP  POST ?type=rate_user            → Node POST api/node/users/{id}/rate
 *   PHP  POST ?type=upload_user_avatar   → Node POST api/node/user/avatars/upload  (вже є в NodeApi)
 *
 * Відповіді сумісні з існуючими моделями [GetUserDataResponse], [UpdateUserDataResponse],
 * [GetUserRatingResponse], [RateUserResponse] — поле user_data є в обох.
 */
interface NodeProfileApi {

    // ─── Own profile ──────────────────────────────────────────────────────────

    /** Отримати власний профіль (повний: privacy, notifications, email, phone). */
    @GET("api/node/users/me")
    suspend fun getMyProfile(): GetUserDataResponse

    /**
     * Оновити поля профілю.
     * Передавай лише ті поля що змінюються — null-поля ігноруються сервером.
     */
    @FormUrlEncoded
    @PUT("api/node/users/me")
    suspend fun updateMyProfile(
        @Field("first_name")   firstName:   String? = null,
        @Field("last_name")    lastName:    String? = null,
        @Field("about")        about:       String? = null,
        @Field("birthday")     birthday:    String? = null,
        @Field("gender")       gender:      String? = null,
        @Field("address")      address:     String? = null,
        @Field("city")         city:        String? = null,
        @Field("state")        state:       String? = null,
        @Field("website")      website:     String? = null,
        @Field("working")      working:     String? = null,
        @Field("school")       school:      String? = null,
        @Field("language")     language:    String? = null,
        @Field("facebook")     facebook:    String? = null,
        @Field("twitter")      twitter:     String? = null,
        @Field("instagram")    instagram:   String? = null,
        @Field("linkedin")     linkedin:    String? = null,
        @Field("youtube")      youtube:     String? = null,
        @Field("username")     username:    String? = null,
    ): UpdateUserDataResponse

    /** Змінити пароль (вимагає current_password). */
    @FormUrlEncoded
    @PUT("api/node/users/me/password")
    suspend fun changePassword(
        @Field("current_password") currentPassword: String,
        @Field("new_password")     newPassword:     String,
    ): UpdateUserDataResponse

    /** Оновити налаштування приватності. */
    @FormUrlEncoded
    @PUT("api/node/users/me/privacy")
    suspend fun updatePrivacy(
        @Field("follow_privacy")          followPrivacy:         String? = null,
        @Field("message_privacy")         messagePrivacy:        String? = null,
        @Field("birth_privacy")           birthPrivacy:          String? = null,
        @Field("friend_privacy")          friendPrivacy:         String? = null,
        @Field("visit_privacy")           visitPrivacy:          String? = null,
        @Field("showlastseen")            showLastSeen:          String? = null,
        @Field("confirm_followers")       confirmFollowers:      String? = null,
        @Field("show_activities_privacy") showActivitiesPrivacy: String? = null,
        @Field("share_my_location")       shareMyLocation:       String? = null,
    ): UpdateUserDataResponse

    // ─── Other users ──────────────────────────────────────────────────────────

    /** Отримати профіль будь-якого користувача за ID. */
    @GET("api/node/users/{id}")
    suspend fun getUserProfile(
        @Path("id") userId: Long,
    ): GetUserDataResponse

    /** Пошук користувачів за ім'ям або username. */
    @GET("api/node/users/search")
    suspend fun searchUsers(
        @Query("q")      query:  String,
        @Query("limit")  limit:  Int = 20,
        @Query("offset") offset: Int = 0,
    ): NodeSearchUsersResponse

    // ─── Social graph ─────────────────────────────────────────────────────────

    @GET("api/node/users/{id}/followers")
    suspend fun getFollowers(
        @Path("id")      userId: Long,
        @Query("limit")  limit:  Int = 30,
        @Query("offset") offset: Int = 0,
    ): NodeFollowListResponse

    @GET("api/node/users/{id}/following")
    suspend fun getFollowing(
        @Path("id")      userId: Long,
        @Query("limit")  limit:  Int = 30,
        @Query("offset") offset: Int = 0,
    ): NodeFollowListResponse

    @POST("api/node/users/{id}/follow")
    suspend fun followUser(@Path("id") userId: Long): NodeFollowActionResponse

    @DELETE("api/node/users/{id}/follow")
    suspend fun unfollowUser(@Path("id") userId: Long): NodeFollowActionResponse

    // ─── Blocking ─────────────────────────────────────────────────────────────

    @GET("api/node/users/me/blocked")
    suspend fun getBlockedUsers(): NodeFollowListResponse

    @POST("api/node/users/{id}/block")
    suspend fun blockUser(@Path("id") userId: Long): UpdateUserDataResponse

    @DELETE("api/node/users/{id}/block")
    suspend fun unblockUser(@Path("id") userId: Long): UpdateUserDataResponse

    // ─── Rating / karma ───────────────────────────────────────────────────────

    /**
     * Отримати рейтинг користувача.
     * @param includeDetails "1" — включити список усіх голосів
     */
    @GET("api/node/users/{id}/rating")
    suspend fun getUserRating(
        @Path("id")               userId:         Long,
        @Query("include_details") includeDetails: String = "0",
    ): GetUserRatingResponse

    /**
     * Поставити або зняти оцінку.
     * Якщо rating_type збігається з поточним голосом — оцінка знімається (toggle).
     */
    @FormUrlEncoded
    @POST("api/node/users/{id}/rate")
    suspend fun rateUser(
        @Path("id")           userId:     Long,
        @Field("rating_type") ratingType: String,        // "like" | "dislike"
        @Field("comment")     comment:    String? = null,
    ): RateUserResponse
}

// ─── Auxiliary response models ────────────────────────────────────────────────

data class NodeSearchUsersResponse(
    @SerializedName("api_status")    val apiStatus:    Int,
    // Returns SearchUser (matches PHP UserSearchResponse) so existing UI code doesn't need changes
    @SerializedName("users")         val users:        List<SearchUser>?,
    @SerializedName("count")         val count:        Int?,
    @SerializedName("offset")        val offset:       Int?,
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class NodeFollowListResponse(
    @SerializedName("api_status")    val apiStatus:    Int,
    @SerializedName("followers")     val followers:    List<User>? = null,
    @SerializedName("following")     val following:    List<User>? = null,
    // Returns BlockedUser so BlockedUsersViewModel doesn't need type adaptation
    @SerializedName("blocked")       val blocked:      List<com.worldmates.messenger.data.model.BlockedUser>? = null,
    @SerializedName("count")         val count:        Int?        = null,
    @SerializedName("error_message") val errorMessage: String?     = null,
)

data class NodeFollowActionResponse(
    @SerializedName("api_status")    val apiStatus:    Int,
    @SerializedName("message")       val message:      String?,
    @SerializedName("status")        val status:       String?,   // "following" | "pending" | "not_following"
    @SerializedName("error_message") val errorMessage: String? = null,
)
