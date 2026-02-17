package com.worldmates.messenger.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.worldmates.messenger.data.model.StrapiContentPack
import com.worldmates.messenger.data.model.toStrapiContentPack
import com.worldmates.messenger.network.StrapiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Репозиторій для роботи зі стікерами, гіфками та емодзі через Strapi CMS
 * Автоматично оновлює контент без потреби перекомпіляції додатку
 */
class StrapiStickerRepository(private val context: Context) {

    companion object {
        private const val TAG = "StrapiStickerRepo"
        private const val PREFS_NAME = "strapi_stickers_cache"
        private const val KEY_ALL_PACKS = "all_packs"
        private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"
        private const val CACHE_VALIDITY_MS = 3600000L // 1 година

        @Volatile
        private var INSTANCE: StrapiStickerRepository? = null

        fun getInstance(context: Context): StrapiStickerRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StrapiStickerRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // StateFlow для стану завантаження
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // StateFlow для всіх пакетів
    private val _allPacks = MutableStateFlow<List<StrapiContentPack>>(emptyList())
    val allPacks: StateFlow<List<StrapiContentPack>> = _allPacks.asStateFlow()

    // StateFlow для стікерів
    private val _stickerPacks = MutableStateFlow<List<StrapiContentPack>>(emptyList())
    val stickerPacks: StateFlow<List<StrapiContentPack>> = _stickerPacks.asStateFlow()

    // StateFlow для гіфок
    private val _gifPacks = MutableStateFlow<List<StrapiContentPack>>(emptyList())
    val gifPacks: StateFlow<List<StrapiContentPack>> = _gifPacks.asStateFlow()

    // StateFlow для емодзі
    private val _emojiPacks = MutableStateFlow<List<StrapiContentPack>>(emptyList())
    val emojiPacks: StateFlow<List<StrapiContentPack>> = _emojiPacks.asStateFlow()

    // Помилки
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Завантажуємо кешовані дані при ініціалізації
        loadFromCache()
    }

    /**
     * Завантажити всі пакети з Strapi
     * @param forceRefresh - примусове оновлення, ігноруючи кеш
     */
    suspend fun fetchAllPacks(forceRefresh: Boolean = false): Result<List<StrapiContentPack>> {
        return withContext(Dispatchers.IO) {
            try {
                _isLoading.value = true
                _error.value = null

                // Перевіряємо кеш
                if (!forceRefresh && isCacheValid()) {
                    val cached = _allPacks.value
                    if (cached.isNotEmpty()) {
                        Log.d(TAG, "Використано кеш: ${cached.size} пакетів")
                        _isLoading.value = false
                        return@withContext Result.success(cached)
                    }
                }

                // Завантажуємо з Strapi
                Log.d(TAG, "Завантаження пакетів з Strapi...")
                val response = StrapiClient.api.getAllContent()

                val packs = (response.data ?: emptyList()).mapNotNull {
                    try {
                        it.toStrapiContentPack(StrapiClient.CDN_URL)
                    } catch (e: Exception) {
                        Log.w(TAG, "Помилка парсингу паку ${it.id}: ${e.message}")
                        null
                    }
                }
                Log.d(TAG, "✅ Завантажено ${packs.size} пакетів з Strapi")

                // Логування деталей
                packs.forEach { pack ->
                    Log.d(TAG, "  📦 ${pack.name} (${pack.type}): ${pack.items.size} елементів")
                }

                // Оновлюємо StateFlow
                updatePacks(packs)

                // Зберігаємо в кеш
                saveToCache(packs)

                _isLoading.value = false
                Result.success(packs)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Помилка завантаження пакетів", e)
                _error.value = "Помилка завантаження: ${e.message}"
                _isLoading.value = false

                // Якщо є кеш - використовуємо його
                val cached = _allPacks.value
                if (cached.isNotEmpty()) {
                    Log.d(TAG, "Використано старий кеш після помилки")
                    return@withContext Result.success(cached)
                }

                Result.failure(e)
            }
        }
    }

    /**
     * Завантажити тільки стікери
     */
    suspend fun fetchStickers(): Result<List<StrapiContentPack>> {
        return withContext(Dispatchers.IO) {
            try {
                _isLoading.value = true
                val response = StrapiClient.api.getContentByType("sticker")
                val packs = (response.data ?: emptyList()).map { it.toStrapiContentPack(StrapiClient.CDN_URL) }
                _stickerPacks.value = packs
                _isLoading.value = false
                Log.d(TAG, "Завантажено ${packs.size} паків стікерів")
                Result.success(packs)
            } catch (e: Exception) {
                Log.e(TAG, "Помилка завантаження стікерів", e)
                _error.value = e.message
                _isLoading.value = false
                Result.failure(e)
            }
        }
    }

    /**
     * Завантажити тільки GIF
     */
    suspend fun fetchGifs(): Result<List<StrapiContentPack>> {
        return withContext(Dispatchers.IO) {
            try {
                _isLoading.value = true
                val response = StrapiClient.api.getContentByType("gif")
                val packs = (response.data ?: emptyList()).map { it.toStrapiContentPack(StrapiClient.CDN_URL) }
                _gifPacks.value = packs
                _isLoading.value = false
                Log.d(TAG, "Завантажено ${packs.size} паків GIF")
                Result.success(packs)
            } catch (e: Exception) {
                Log.e(TAG, "Помилка завантаження GIF", e)
                _error.value = e.message
                _isLoading.value = false
                Result.failure(e)
            }
        }
    }

    /**
     * Завантажити тільки емодзі
     */
    suspend fun fetchEmojis(): Result<List<StrapiContentPack>> {
        return withContext(Dispatchers.IO) {
            try {
                _isLoading.value = true
                val response = StrapiClient.api.getContentByType("emoji")
                val packs = (response.data ?: emptyList()).map { it.toStrapiContentPack(StrapiClient.CDN_URL) }
                _emojiPacks.value = packs
                _isLoading.value = false
                Log.d(TAG, "Завантажено ${packs.size} паків емодзі")
                Result.success(packs)
            } catch (e: Exception) {
                Log.e(TAG, "Помилка завантаження емодзі", e)
                _error.value = e.message
                _isLoading.value = false
                Result.failure(e)
            }
        }
    }

    /**
     * Отримати пакет по slug
     */
    suspend fun getPackBySlug(slug: String): Result<StrapiContentPack?> {
        return withContext(Dispatchers.IO) {
            try {
                val response = StrapiClient.api.getPackBySlug(slug)
                val pack = response.data?.firstOrNull()?.toStrapiContentPack(StrapiClient.CDN_URL)
                Result.success(pack)
            } catch (e: Exception) {
                Log.e(TAG, "Помилка завантаження паку $slug", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Оновити StateFlow з усіма пакетами та відфільтрованими по типу
     */
    private fun updatePacks(packs: List<StrapiContentPack>) {
        _allPacks.value = packs
        _stickerPacks.value = packs.filter { it.type == StrapiContentPack.ContentType.STICKER }
        _gifPacks.value = packs.filter { it.type == StrapiContentPack.ContentType.GIF }
        _emojiPacks.value = packs.filter { it.type == StrapiContentPack.ContentType.EMOJI }
    }

    /**
     * Перевірка валідності кешу
     */
    private fun isCacheValid(): Boolean {
        val timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0)
        val now = System.currentTimeMillis()
        val isValid = (now - timestamp) < CACHE_VALIDITY_MS
        Log.d(TAG, "Кеш ${if (isValid) "валідний" else "застарів"} (${(now - timestamp) / 1000}с)")
        return isValid
    }

    /**
     * Завантажити з кешу
     */
    private fun loadFromCache() {
        try {
            val json = prefs.getString(KEY_ALL_PACKS, null) ?: return
            val type = object : TypeToken<List<StrapiContentPack>>() {}.type
            val packs: List<StrapiContentPack> = gson.fromJson(json, type)

            if (packs.isNotEmpty()) {
                updatePacks(packs)
                Log.d(TAG, "Завантажено з кешу: ${packs.size} пакетів")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка читання з кешу", e)
        }
    }

    /**
     * Зберегти в кеш
     */
    private fun saveToCache(packs: List<StrapiContentPack>) {
        try {
            val json = gson.toJson(packs)
            prefs.edit()
                .putString(KEY_ALL_PACKS, json)
                .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Збережено в кеш: ${packs.size} пакетів")
        } catch (e: Exception) {
            Log.e(TAG, "Помилка збереження в кеш", e)
        }
    }

    /**
     * Очистити кеш
     */
    fun clearCache() {
        prefs.edit().clear().apply()
        _allPacks.value = emptyList()
        _stickerPacks.value = emptyList()
        _gifPacks.value = emptyList()
        _emojiPacks.value = emptyList()
        Log.d(TAG, "Кеш очищено")
    }

    /**
     * Примусово оновити контент
     */
    suspend fun refresh() {
        fetchAllPacks(forceRefresh = true)
    }
}