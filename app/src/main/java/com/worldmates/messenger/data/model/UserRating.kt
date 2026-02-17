package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

/**
 * Модель рейтингу користувача (система карми/довіри)
 */
data class UserRating(
    @SerializedName("user_id")
    val userId: Long = 0,

    @SerializedName("username")
    val username: String? = null,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("avatar")
    val avatar: String? = null,

    @SerializedName("likes")
    val likes: Int = 0,

    @SerializedName("dislikes")
    val dislikes: Int = 0,

    @SerializedName("score")
    val score: Float = 0f,

    @SerializedName("trust_level")
    val trustLevel: String = "neutral", // verified, trusted, neutral, untrusted

    @SerializedName("trust_level_label")
    val trustLevelLabel: String = "Нейтральний",

    @SerializedName("trust_level_emoji")
    val trustLevelEmoji: String = "🔵",

    @SerializedName("trust_level_color")
    val trustLevelColor: String = "#9E9E9E",

    @SerializedName("total_ratings")
    val totalRatings: Int = 0,

    @SerializedName("like_percentage")
    val likePercentage: Float = 0f,

    @SerializedName("dislike_percentage")
    val dislikePercentage: Float = 0f,

    @SerializedName("my_rating")
    val myRating: MyRating? = null
)

/**
 * Моя оцінка користувача
 */
data class MyRating(
    @SerializedName("type")
    val type: String? = null, // "like" or "dislike"

    @SerializedName("comment")
    val comment: String? = null,

    @SerializedName("created_at")
    val createdAt: String? = null
)

/**
 * Детальна оцінка від користувача
 */
data class RatingDetail(
    @SerializedName("id")
    val id: Long,

    @SerializedName("rater_id")
    val raterId: Long,

    @SerializedName("rater_username")
    val raterUsername: String,

    @SerializedName("rater_name")
    val raterName: String,

    @SerializedName("rater_avatar")
    val raterAvatar: String?,

    @SerializedName("rating_type")
    val ratingType: String, // "like" or "dislike"

    @SerializedName("comment")
    val comment: String?,

    @SerializedName("created_at")
    val createdAt: String
)

/**
 * Відповідь API для оцінки користувача
 */
data class RateUserResponse(
    @SerializedName("api_status")
    val apiStatus: Int,

    @SerializedName("message")
    val message: String?,

    @SerializedName("action")
    val action: String?, // "added", "updated", "removed"

    @SerializedName("rating_type")
    val ratingType: String?, // "like", "dislike", or null if removed

    @SerializedName("user_rating")
    val userRating: UserRating?,

    @SerializedName("error_message")
    val errorMessage: String?
)

/**
 * Відповідь API для отримання рейтингу
 */
data class GetUserRatingResponse(
    @SerializedName("api_status")
    val apiStatus: Int,

    @SerializedName("rating")
    val rating: UserRating?,

    @SerializedName("ratings_list")
    val ratingsList: List<RatingDetail>?,

    @SerializedName("ratings_count")
    val ratingsCount: Int?,

    @SerializedName("error_message")
    val errorMessage: String?
)
