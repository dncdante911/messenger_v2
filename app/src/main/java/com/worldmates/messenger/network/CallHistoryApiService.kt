package com.worldmates.messenger.network

import com.worldmates.messenger.data.model.CallHistoryActionResponse
import com.worldmates.messenger.data.model.GetCallHistoryResponse
import retrofit2.http.*

/**
 * API сервіс для історії дзвінків
 */
interface CallHistoryApiService {

    /**
     * Отримати історію дзвінків
     * @param filter Фільтр: all, missed, incoming, outgoing
     * @param limit Кількість записів
     * @param offset Зсув для пагінації
     */
    @FormUrlEncoded
    @POST("/api/v2/call_history.php")
    suspend fun getCallHistory(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "get_history",
        @Field("filter") filter: String = "all",
        @Field("limit") limit: Int = 50,
        @Field("offset") offset: Int = 0
    ): GetCallHistoryResponse

    /**
     * Видалити дзвінок з історії
     */
    @FormUrlEncoded
    @POST("/api/v2/call_history.php")
    suspend fun deleteCall(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "delete_call",
        @Field("call_id") callId: Long
    ): CallHistoryActionResponse

    /**
     * Очистити всю історію дзвінків
     */
    @FormUrlEncoded
    @POST("/api/v2/call_history.php")
    suspend fun clearHistory(
        @Query("access_token") accessToken: String,
        @Field("type") type: String = "clear_history"
    ): CallHistoryActionResponse
}
