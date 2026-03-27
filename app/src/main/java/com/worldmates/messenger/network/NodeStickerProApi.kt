package com.worldmates.messenger.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Node.js Sticker PRO Packs API
 *
 * Endpoints (port 449):
 *   GET  /api/node/stickers/strapi-meta  — PRO metadata for Strapi packs
 *   POST /api/node/stickers/strapi-buy   — purchase a PRO pack with WorldStars
 */
interface NodeStickerProApi {

    /** Returns PRO metadata for all known Strapi sticker packs for this user. */
    @GET("api/node/stickers/strapi-meta")
    suspend fun getStrapiMeta(): StickerProMetaResponse

    /** Purchase a PRO sticker pack with WorldStars. */
    @FormUrlEncoded
    @POST("api/node/stickers/strapi-buy")
    suspend fun buyPack(
        @Field("slug") slug: String,
    ): StickerBuyResponse
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────

data class StickerProMetaResponse(
    @SerializedName("api_status")    val apiStatus:    Int = 0,
    @SerializedName("packs")         val packs:        List<StickerProPackMeta> = emptyList(),
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class StickerProPackMeta(
    @SerializedName("slug")         val slug:        String = "",
    @SerializedName("is_pro")       val isPro:       Boolean = false,
    @SerializedName("stars_price")  val starsPrice:  Int = 0,
    @SerializedName("is_purchased") val isPurchased: Boolean = false,
)

data class StickerBuyResponse(
    @SerializedName("api_status")    val apiStatus:    Int = 0,
    @SerializedName("new_balance")   val newBalance:   Int = 0,
    @SerializedName("already_owned") val alreadyOwned: Boolean = false,
    @SerializedName("error_message") val errorMessage: String? = null,
)
