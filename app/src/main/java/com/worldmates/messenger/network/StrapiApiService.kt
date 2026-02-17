package com.worldmates.messenger.network

import com.worldmates.messenger.data.model.StrapiResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API сервіс для роботи зі Strapi CMS
 * Базовий URL: https://cdn.worldmates.club/
 */
interface StrapiApiService {

    /**
     * Отримати всі пакети стікерів/гіфок з усім вмістом
     * populate=* - завантажує всі вкладені медіа файли
     */
    @GET("api/gifs-packs?populate=*")
    suspend fun getAllContent(): StrapiResponse

    /**
     * Отримати пакети з фільтрацією по типу
     * @param type - тип контенту: "sticker", "gif", або "emoji"
     */
    @GET("api/gifs-packs?populate=*")
    suspend fun getContentByType(
        @Query("filters[gif][\$eq]") type: String
    ): StrapiResponse

    /**
     * Отримати пакет по slug
     */
    @GET("api/gifs-packs?populate=*")
    suspend fun getPackBySlug(
        @Query("filters[slug][\$eq]") slug: String
    ): StrapiResponse

    /**
     * Отримати пакети з пагінацією
     */
    @GET("api/gifs-packs?populate=*")
    suspend fun getContentPaginated(
        @Query("pagination[page]") page: Int = 1,
        @Query("pagination[pageSize]") pageSize: Int = 25
    ): StrapiResponse
}