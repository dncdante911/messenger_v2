package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

/**
 * Модель кастомного емоджі
 */
data class CustomEmoji(
    @SerializedName("id") val id: Long,
    @SerializedName("code") val code: String,           // :custom_smile:
    @SerializedName("url") val url: String,             // URL зображення
    @SerializedName("pack_id") val packId: Long,
    @SerializedName("name") val name: String? = null,   // Назва емоджі
    @SerializedName("keywords") val keywords: List<String>? = null,  // Ключові слова для пошуку
    @SerializedName("created_at") val createdAt: String? = null
)

/**
 * Пак кастомних емоджі
 */
data class EmojiPack(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("icon_url") val iconUrl: String? = null,
    @SerializedName("author") val author: String? = null,
    @SerializedName("emojis") val emojis: List<CustomEmoji>? = null,
    @SerializedName("is_active") val isActive: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

/**
 * Відповідь API зі списком паків емоджі
 */
data class EmojiPacksResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("packs") val packs: List<EmojiPack>? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

/**
 * Відповідь API з емоджі конкретного паку
 */
data class EmojiPackDetailResponse(
    @SerializedName("api_status") val apiStatus: Int,
    @SerializedName("pack") val pack: EmojiPack? = null,
    @SerializedName("emojis") val emojis: List<CustomEmoji>? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)
