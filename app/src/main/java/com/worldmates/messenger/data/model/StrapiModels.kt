package com.worldmates.messenger.data.model

import com.google.gson.annotations.SerializedName

/**
 * Strapi Response для пакетів стікерів/гіфок
 */
data class StrapiResponse(
    @SerializedName("data") val data: List<PackItem>? = null,
    @SerializedName("meta") val meta: Meta? = null
)

/**
 * Окремий пакет (стікери або гіфки)
 */
data class PackItem(
    @SerializedName("id") val id: Int,
    @SerializedName("attributes") val attributes: PackAttributes
)

/**
 * Атрибути пакету
 */
data class PackAttributes(
    @SerializedName("Name_pack") val namePack: String? = null,
    @SerializedName("gif") val type: String? = null, // "sticker", "gif", або "emoji"
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("upload_gifs") val uploadGifs: MediaWrapper? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

/**
 * Обгортка для медіа файлів
 * data може бути null якщо в Strapi немає завантажених файлів
 */
data class MediaWrapper(
    @SerializedName("data") val data: List<MediaItem>? = null
)

/**
 * Окремий медіа файл
 */
data class MediaItem(
    @SerializedName("id") val id: Int,
    @SerializedName("attributes") val attributes: MediaAttributes
)

/**
 * Атрибути медіа файлу
 */
data class MediaAttributes(
    @SerializedName("url") val url: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("size") val size: Double? = null,
    @SerializedName("mime") val mime: String? = null
)

/**
 * Мета інформація про пагінацію
 */
data class Meta(
    @SerializedName("pagination") val pagination: Pagination
)

data class Pagination(
    @SerializedName("page") val page: Int,
    @SerializedName("pageSize") val pageSize: Int,
    @SerializedName("pageCount") val pageCount: Int,
    @SerializedName("total") val total: Int
)

/**
 * Локальна модель для зручності використання (Strapi контент)
 */
data class StrapiContentPack(
    val id: Int,
    val name: String,
    val type: ContentType,
    val slug: String,
    val items: List<StrapiContentItem>
) {
    enum class ContentType {
        STICKER,
        GIF,
        EMOJI;

        companion object {
            fun fromString(value: String): ContentType {
                return when (value.lowercase()) {
                    "sticker" -> STICKER
                    "gif" -> GIF
                    "emoji" -> EMOJI
                    else -> STICKER
                }
            }
        }
    }
}

/**
 * Окремий стікер/гіфка/емодзі з Strapi
 */
data class StrapiContentItem(
    val id: Int,
    val url: String,
    val name: String?,
    val width: Int?,
    val height: Int?
)

/**
 * Extension для конвертації Strapi моделі в локальну
 */
fun PackItem.toStrapiContentPack(cdnUrl: String = "https://cdn.worldmates.club"): StrapiContentPack {
    return StrapiContentPack(
        id = id,
        name = attributes.namePack ?: "Pack #$id",
        type = StrapiContentPack.ContentType.fromString(attributes.type ?: "sticker"),
        slug = attributes.slug ?: "pack-$id",
        items = attributes.uploadGifs?.data?.map { mediaItem ->
            StrapiContentItem(
                id = mediaItem.id,
                url = cdnUrl + mediaItem.attributes.url,
                name = mediaItem.attributes.name,
                width = mediaItem.attributes.width,
                height = mediaItem.attributes.height
            )
        } ?: emptyList()
    )
}