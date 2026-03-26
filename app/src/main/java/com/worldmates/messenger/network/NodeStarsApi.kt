package com.worldmates.messenger.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Node.js WorldStars REST API
 *
 * Endpoints on port 449:
 *   GET  /api/node/stars/balance
 *   GET  /api/node/stars/transactions
 *   GET  /api/node/stars/packs
 *   POST /api/node/stars/send
 *   POST /api/node/stars/purchase
 */
interface NodeStarsApi {

    /** Баланс + останні 10 транзакцій. */
    @GET("api/node/stars/balance")
    suspend fun getBalance(): StarsBalanceResponse

    /** Пагінована історія транзакцій. */
    @GET("api/node/stars/transactions")
    suspend fun getTransactions(
        @Query("limit")  limit:  Int = 20,
        @Query("offset") offset: Int = 0,
    ): StarsTransactionsResponse

    /** Список пакетів зірок для покупки. */
    @GET("api/node/stars/packs")
    suspend fun getPacks(): StarsPacksResponse

    /** Надіслати зірки іншому користувачу. */
    @FormUrlEncoded
    @POST("api/node/stars/send")
    suspend fun sendStars(
        @Field("to_user_id") toUserId: Int,
        @Field("amount")     amount:   Int,
        @Field("note")       note:     String? = null,
    ): StarsSendResponse

    /** Ініціювати оплату пакету зірок. */
    @FormUrlEncoded
    @POST("api/node/stars/purchase")
    suspend fun purchase(
        @Field("pack_id")  packId:   Int,
        @Field("provider") provider: String = "wayforpay",
    ): StarsPurchaseResponse
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

data class StarsBalanceResponse(
    @SerializedName("api_status")          val apiStatus:          Int = 0,
    @SerializedName("balance")             val balance:            Int = 0,
    @SerializedName("total_purchased")     val totalPurchased:     Int = 0,
    @SerializedName("total_sent")          val totalSent:          Int = 0,
    @SerializedName("total_received")      val totalReceived:      Int = 0,
    @SerializedName("recent_transactions") val recentTransactions: List<StarsTransaction> = emptyList(),
    @SerializedName("error_message")       val errorMessage:       String? = null,
)

data class StarsTransactionsResponse(
    @SerializedName("api_status")    val apiStatus:    Int = 0,
    @SerializedName("transactions")  val transactions: List<StarsTransaction> = emptyList(),
    @SerializedName("limit")         val limit:        Int = 20,
    @SerializedName("offset")        val offset:       Int = 0,
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class StarsPacksResponse(
    @SerializedName("api_status")    val apiStatus:    Int = 0,
    @SerializedName("packs")         val packs:        List<StarsPack> = emptyList(),
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class StarsSendResponse(
    @SerializedName("api_status")    val apiStatus:   Int = 0,
    @SerializedName("new_balance")   val newBalance:  Int = 0,
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class StarsPurchaseResponse(
    @SerializedName("api_status")    val apiStatus:   Int = 0,
    @SerializedName("provider")      val provider:    String = "",
    @SerializedName("payment_url")   val paymentUrl:  String = "",
    @SerializedName("order_id")      val orderId:     String = "",
    @SerializedName("pack")          val pack:        StarsPack? = null,
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class StarsTransaction(
    @SerializedName("id")               val id:             Int = 0,
    @SerializedName("from_user_id")     val fromUserId:     Int? = null,
    @SerializedName("to_user_id")       val toUserId:       Int = 0,
    @SerializedName("amount")           val amount:         Int = 0,
    @SerializedName("type")             val type:           String = "",  // purchase|send|receive|refund
    @SerializedName("ref_type")         val refType:        String? = null,
    @SerializedName("ref_id")           val refId:          Int? = null,
    @SerializedName("note")             val note:           String? = null,
    @SerializedName("created_at")       val createdAt:      String = "",
    @SerializedName("other_user_name")  val otherUserName:  String? = null,
    @SerializedName("other_user_avatar")val otherUserAvatar:String? = null,
)

data class StarsPack(
    @SerializedName("id")         val id:        Int = 0,
    @SerializedName("stars")      val stars:     Int = 0,
    @SerializedName("price_uah")  val priceUah:  Int = 0,
    @SerializedName("is_popular") val isPopular: Boolean = false,
    @SerializedName("label")      val label:     String = "",
)
