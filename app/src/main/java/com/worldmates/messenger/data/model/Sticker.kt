package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

/**
 * Модель стікера
 */
data class Sticker(
    @SerializedName("id") val id: Long,
    @SerializedName("pack_id") val packId: Long,
    @SerializedName("file_url") val fileUrl: String,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("emoji") val emoji: String? = null,  // Відповідний емоджі
    @SerializedName("keywords") val keywords: List<String>? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("file_size") val fileSize: Long? = null,
    @SerializedName("format") val format: String? = null  // webp, png, gif
)

/**
 * Пак стікерів
 */
data class StickerPack(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("icon_url") val iconUrl: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("author") val author: String? = null,
    @SerializedName("stickers") val stickers: List<Sticker>? = null,
    @SerializedName("sticker_count") val stickerCount: Int? = null,
    @SerializedName("is_active") val isActive: Boolean = false,
    @SerializedName("is_animated") val isAnimated: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

/**
 * Відповідь API зі списком паків стікерів
 */
data class StickerPacksResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("packs") val packs: List<StickerPack>? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/**
 * Відповідь API з стікерами конкретного паку
 */
data class StickerPackDetailResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("pack") val pack: StickerPack? = null,
    @SerializedName("stickers") val stickers: List<Sticker>? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)
