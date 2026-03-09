package com.worldmates.messenger.network

import com.google.gson.annotations.SerializedName
import com.worldmates.messenger.ui.bots.InlineQueryResult
import retrofit2.http.*

/**
 * Node.js Inline Bot API — Retrofit interface.
 *
 * Endpoints:
 *   POST /api/node/bot/getInlineResults    — отримати результати inline-запиту
 *   POST /api/node/bot/chooseInlineResult  — повідомити бота про вибраний результат
 *
 * Auth: NodeRetrofitClient (access-token header).
 */
interface NodeBotInlineApi {

    @FormUrlEncoded
    @POST("api/node/bot/getInlineResults")
    suspend fun getInlineResults(
        @Field("bot_username") botUsername: String,
        @Field("query") query: String,
        @Field("offset") offset: String = ""
    ): InlineResultsResponse

    @FormUrlEncoded
    @POST("api/node/bot/chooseInlineResult")
    suspend fun chooseInlineResult(
        @Field("bot_username") botUsername: String,
        @Field("result_id") resultId: String,
        @Field("query") query: String = ""
    ): NodeSimpleResponse
}

data class InlineResultsResponse(
    @SerializedName("api_status")   val apiStatus: Int = 0,
    @SerializedName("results")      val results: List<InlineQueryResultDto>? = null,
    @SerializedName("next_offset")  val nextOffset: String? = null,
    @SerializedName("switch_pm_text") val switchPmText: String? = null,
    @SerializedName("switch_pm_parameter") val switchPmParameter: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

data class InlineQueryResultDto(
    @SerializedName("id")          val id: String = "",
    @SerializedName("type")        val type: String = "article",
    @SerializedName("title")       val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("thumb_url")   val thumbUrl: String? = null,
    @SerializedName("input_message_content") val inputMessageContent: InputMessageContentDto? = null,
    @SerializedName("url")         val url: String? = null,
    @SerializedName("hide_url")    val hideUrl: Boolean = false
) {
    fun toUiModel(): InlineQueryResult = InlineQueryResult(
        id          = id,
        type        = type,
        title       = title,
        description = description,
        thumbUrl    = thumbUrl,
        inputMessageContent = inputMessageContent?.messageText,
        url         = url,
        hideUrl     = hideUrl
    )
}

data class InputMessageContentDto(
    @SerializedName("message_text") val messageText: String? = null,
    @SerializedName("parse_mode")   val parseMode: String? = null
)
