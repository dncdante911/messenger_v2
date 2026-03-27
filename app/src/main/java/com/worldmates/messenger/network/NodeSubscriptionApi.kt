package com.worldmates.messenger.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Node.js Subscription REST API
 *
 * Endpoints on port 449:
 *   GET  /api/node/subscription/status
 *   POST /api/node/subscription/create-payment
 *   POST /api/node/subscription/start-trial    — activate 7-day free trial (once per account)
 *   POST /api/node/subscription/gift           — gift PRO to another user via WorldStars
 *   GET  /api/node/subscription/gift-price     — pricing info for gift UI
 */
interface NodeSubscriptionApi {

    /** Returns current subscription status for the authenticated user. */
    @GET("api/node/subscription/status")
    suspend fun getStatus(): NodeSubscriptionStatusResponse

    /**
     * Initiates a payment for N months via the specified provider.
     * @param months 1..24
     * @param provider "wayforpay", "liqpay", or "monobank"
     */
    @FormUrlEncoded
    @POST("api/node/subscription/create-payment")
    suspend fun createPayment(
        @Field("months")   months:   Int,
        @Field("provider") provider: String
    ): NodeCreatePaymentResponse

    /**
     * Activates a 7-day free trial. One-time per account.
     * If already used, returns already_used=true (not an error).
     */
    @POST("api/node/subscription/start-trial")
    suspend fun startTrial(): NodeTrialResponse

    /**
     * Gift PRO subscription to another user, paid with WorldStars.
     * Cost: GIFT_PRICE_STARS_MONTH × months  (server-side constant, see gift-price endpoint).
     * @param toUserId  recipient user ID
     * @param months    1..12
     */
    @FormUrlEncoded
    @POST("api/node/subscription/gift")
    suspend fun giftSubscription(
        @Field("to_user_id") toUserId: Int,
        @Field("months")     months:   Int,
    ): NodeGiftResponse

    /** Returns current gift pricing (stars per month, available plans). */
    @GET("api/node/subscription/gift-price")
    suspend fun getGiftPrice(): NodeGiftPriceResponse
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

data class NodeSubscriptionStatusResponse(
    @SerializedName("api_status")  val apiStatus:  Int = 0,
    @SerializedName("is_pro")      val isPro:      Int = 0,
    @SerializedName("pro_type")    val proType:    Int = 0,
    @SerializedName("pro_time")    val proTime:    Long = 0L,
    @SerializedName("days_left")   val daysLeft:   Int = 0,
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class NodeCreatePaymentResponse(
    @SerializedName("api_status")  val apiStatus:   Int = 0,
    @SerializedName("provider")    val provider:    String = "",
    @SerializedName("payment_url") val paymentUrl:  String = "",
    @SerializedName("order_id")    val orderId:     String = "",
    @SerializedName("amount_uah")  val amountUah:   Int = 0,
    @SerializedName("months")      val months:      Int = 0,
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class NodeTrialResponse(
    @SerializedName("api_status")   val apiStatus:   Int = 0,
    @SerializedName("already_used") val alreadyUsed: Boolean = false,
    @SerializedName("trial_days")   val trialDays:   Int = 0,
    @SerializedName("pro_time")     val proTime:     Long = 0L,
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class NodeGiftResponse(
    @SerializedName("api_status")          val apiStatus:        Int = 0,
    @SerializedName("months")              val months:           Int = 0,
    @SerializedName("stars_spent")         val starsSpent:       Int = 0,
    @SerializedName("new_balance")         val newBalance:       Int = 0,
    @SerializedName("recipient_pro_time")  val recipientProTime: Long = 0L,
    @SerializedName("stars_per_month")     val starsPerMonth:    Int = 0,
    @SerializedName("error_message")       val errorMessage:     String? = null,
)

data class NodeGiftPriceResponse(
    @SerializedName("api_status")      val apiStatus:     Int = 0,
    @SerializedName("stars_per_month") val starsPerMonth: Int = 0,
    @SerializedName("trial_days")      val trialDays:     Int = 0,
    @SerializedName("plans")           val plans:         List<GiftPlan> = emptyList(),
    @SerializedName("error_message")   val errorMessage:  String? = null,
)

data class GiftPlan(
    @SerializedName("months") val months: Int = 1,
    @SerializedName("stars")  val stars:  Int = 0,
    @SerializedName("label")  val label:  String = "",
)
