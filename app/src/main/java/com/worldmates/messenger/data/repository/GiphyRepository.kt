package com.worldmates.messenger.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 🎬 GIPHY Repository - работа с GIPHY API
 *
 * Использует прямые HTTP запросы к GIPHY REST API
 * (без SDK, чтобы избежать проблем с internal классами)
 *
 * Использование:
 * ```
 * val giphyRepo = GiphyRepository.getInstance(context)
 * val trendingGifs = giphyRepo.fetchTrendingGifs()
 * val searchResults = giphyRepo.searchGifs("funny cats")
 * ```
 */
class GiphyRepository private constructor(
    private val context: Context
) {
    companion object {
        @Volatile
        private var instance: GiphyRepository? = null

        fun getInstance(context: Context): GiphyRepository {
            return instance ?: synchronized(this) {
                instance ?: GiphyRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }

        private const val TAG = "GiphyRepository"
        private const val BASE_URL = "https://api.giphy.com/v1/"

        // GIPHY API Key
        // Production key from https://developers.giphy.com/
        const val GIPHY_API_KEY = "6jkmmXp7Pjkxl4uvXO5AUcL4pl22QrWA"
    }

    // Retrofit API для GIPHY
    private val giphyApi: GiphyApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GiphyApi::class.java)
    }

    // State flows для кэширования
    private val _trendingGifs = MutableStateFlow<List<GifItem>>(emptyList())
    val trendingGifs: StateFlow<List<GifItem>> = _trendingGifs

    private val _searchResults = MutableStateFlow<List<GifItem>>(emptyList())
    val searchResults: StateFlow<List<GifItem>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /**
     * 🔥 Получить Trending GIF (популярные)
     */
    suspend fun fetchTrendingGifs(
        limit: Int = 50,
        offset: Int = 0
    ): Result<List<GifItem>> = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true
            Log.d(TAG, "Fetching trending GIFs...")

            val response = giphyApi.getTrending(
                apiKey = GIPHY_API_KEY,
                limit = limit,
                offset = offset,
                rating = "g"
            )

            if (response.isSuccessful && response.body() != null) {
                val gifs = response.body()!!.data.map { it.toGifItem() }
                _trendingGifs.value = gifs
                Log.d(TAG, "✅ Loaded ${gifs.size} trending GIFs")
                Result.success(gifs)
            } else {
                Log.e(TAG, "❌ Trending GIFs error: ${response.code()}")
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ fetchTrendingGifs error", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * 🔍 Поиск GIF по запросу
     */
    suspend fun searchGifs(
        query: String,
        limit: Int = 50,
        offset: Int = 0
    ): Result<List<GifItem>> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) {
                return@withContext Result.success(emptyList())
            }

            _isLoading.value = true
            Log.d(TAG, "Searching GIFs: $query")

            val response = giphyApi.searchGifs(
                apiKey = GIPHY_API_KEY,
                query = query,
                limit = limit,
                offset = offset,
                rating = "g"
            )

            if (response.isSuccessful && response.body() != null) {
                val gifs = response.body()!!.data.map { it.toGifItem() }
                _searchResults.value = gifs
                Log.d(TAG, "✅ Found ${gifs.size} GIFs for: $query")
                Result.success(gifs)
            } else {
                Log.e(TAG, "❌ Search GIFs error: ${response.code()}")
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ searchGifs error: $query", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * 🎲 Получить случайный GIF
     */
    suspend fun fetchRandomGif(
        tag: String? = null
    ): Result<GifItem?> = withContext(Dispatchers.IO) {
        try {
            _isLoading.value = true

            val response = giphyApi.getRandomGif(
                apiKey = GIPHY_API_KEY,
                tag = tag,
                rating = "g"
            )

            if (response.isSuccessful && response.body() != null) {
                val gif = response.body()!!.data.toGifItem()
                Log.d(TAG, "✅ Random GIF loaded")
                Result.success(gif)
            } else {
                Log.e(TAG, "❌ Random GIF error: ${response.code()}")
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ fetchRandomGif error", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * 📊 Получить URL GIF в разных качествах
     */
    fun getGifUrls(gif: GifItem): GifUrls {
        return GifUrls(
            original = gif.url,
            downsized = gif.downsizedUrl,
            downsizedMedium = gif.downsizedMediumUrl,
            downsizedLarge = gif.downsizedLargeUrl,
            preview = gif.previewUrl,
            fixedWidth = gif.fixedWidthUrl,
            fixedHeight = gif.fixedHeightUrl
        )
    }

    /**
     * Очистить кэш
     */
    fun clearCache() {
        _trendingGifs.value = emptyList()
        _searchResults.value = emptyList()
    }
}

/**
 * GIPHY REST API interface
 */
interface GiphyApi {
    @GET("gifs/trending")
    suspend fun getTrending(
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("rating") rating: String = "g"
    ): Response<GiphyResponse>

    @GET("gifs/search")
    suspend fun searchGifs(
        @Query("api_key") apiKey: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("rating") rating: String = "g"
    ): Response<GiphyResponse>

    @GET("gifs/random")
    suspend fun getRandomGif(
        @Query("api_key") apiKey: String,
        @Query("tag") tag: String? = null,
        @Query("rating") rating: String = "g"
    ): Response<GiphyRandomResponse>
}

/**
 * GIPHY API Response models
 */
data class GiphyResponse(
    val data: List<GiphyGif>,
    val pagination: Pagination? = null
)

data class GiphyRandomResponse(
    val data: GiphyGif
)

data class Pagination(
    val total_count: Int,
    val count: Int,
    val offset: Int
)

data class GiphyGif(
    val id: String,
    val title: String?,
    val images: GiphyImages
) {
    fun toGifItem(): GifItem {
        return GifItem(
            id = id,
            title = title ?: "",
            url = images.original.url,
            previewUrl = images.preview_gif?.url ?: images.downsized.url,
            downsizedUrl = images.downsized.url,
            downsizedMediumUrl = images.downsized_medium?.url ?: images.downsized.url,
            downsizedLargeUrl = images.downsized_large?.url ?: images.original.url,
            fixedWidthUrl = images.fixed_width.url,
            fixedHeightUrl = images.fixed_height.url,
            width = images.original.width.toIntOrNull() ?: 480,
            height = images.original.height.toIntOrNull() ?: 270
        )
    }
}

data class GiphyImages(
    val original: GiphyImageVariant,
    val downsized: GiphyImageVariant,
    val downsized_medium: GiphyImageVariant? = null,
    val downsized_large: GiphyImageVariant? = null,
    val preview_gif: GiphyImageVariant? = null,
    val fixed_width: GiphyImageVariant,
    val fixed_height: GiphyImageVariant
)

data class GiphyImageVariant(
    val url: String,
    val width: String,
    val height: String
)

/**
 * Упрощенная модель GIF для использования в приложении
 */
data class GifItem(
    val id: String,
    val title: String,
    val url: String,
    val previewUrl: String,
    val downsizedUrl: String,
    val downsizedMediumUrl: String,
    val downsizedLargeUrl: String,
    val fixedWidthUrl: String,
    val fixedHeightUrl: String,
    val width: Int,
    val height: Int
)

/**
 * GIF URLs в разных качествах
 */
data class GifUrls(
    val original: String,           // Оригинальное качество (большой размер)
    val downsized: String,           // Сжатый (маленький)
    val downsizedMedium: String,     // Сжатый средний
    val downsizedLarge: String,      // Сжатый большой
    val preview: String,             // Превью (очень маленький)
    val fixedWidth: String,          // Фиксированная ширина
    val fixedHeight: String          // Фиксированная высота
) {
    /**
     * Получить лучший URL для отправки в чат
     * (баланс между качеством и размером)
     */
    fun getBestForChat(): String {
        return downsizedMedium.ifEmpty {
            downsized.ifEmpty {
                fixedWidth.ifEmpty {
                    original
                }
            }
        }
    }

    /**
     * Получить URL для превью с фоллбэками
     */
    fun getPreviewUrl(): String {
        return preview.ifEmpty {
            downsized.ifEmpty {
                fixedWidth
            }
        }
    }
}
