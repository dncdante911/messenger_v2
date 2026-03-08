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
 */
interface NodeSubscriptionApi {

    /** Returns current subscription status for the authenticated user. */
    @GET("api/node/subscription/status")
    suspend fun getStatus(): NodeSubscriptionStatusResponse

    /**
     * Initiates a payment for N months via the specified provider.
     * @param months 1..24
     * @param provider "wayforpay" or "liqpay"
     */
    @FormUrlEncoded
    @POST("api/node/subscription/create-payment")
    suspend fun createPayment(
        @Field("months")   months:   Int,
        @Field("provider") provider: String
    ): NodeCreatePaymentResponse
}

data class NodeSubscriptionStatusResponse(
    @SerializedName("api_status") val apiStatus: Int = 0,
    @SerializedName("is_pro")     val isPro:     Int = 0,
    @SerializedName("pro_type")   val proType:   Int = 0,
    @SerializedName("pro_time")   val proTime:   Long = 0L,
    @SerializedName("days_left")  val daysLeft:  Int = 0,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class NodeCreatePaymentResponse(
    @SerializedName("api_status")  val apiStatus:   Int = 0,
    @SerializedName("provider")    val provider:    String = "",
    @SerializedName("payment_url") val paymentUrl:  String = "",
    @SerializedName("order_id")    val orderId:     String = "",
    @SerializedName("amount_uah")  val amountUah:   Int = 0,
    @SerializedName("months")      val months:      Int = 0,
    @SerializedName("error_message") val errorMessage: String? = null
)
