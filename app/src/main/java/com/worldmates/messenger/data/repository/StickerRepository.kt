package com.worldmates.messenger.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.worldmates.messenger.data.model.Sticker
import com.worldmates.messenger.data.model.StickerPack
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Репозиторій для роботи з кастомними стікерами
 */
class StickerRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sticker_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _stickerPacks = MutableStateFlow<List<StickerPack>>(emptyList())
    val stickerPacks: StateFlow<List<StickerPack>> = _stickerPacks

    private val _activeStickers = MutableStateFlow<List<Sticker>>(emptyList())
    val activeStickers: StateFlow<List<Sticker>> = _activeStickers

    init {
        // Завантажуємо кешовані стікери при ініціалізації
        loadCachedStickers()
    }

    /**
     * Завантажує список паків стікерів з API
     */
    suspend fun fetchStickerPacks(): Result<List<StickerPack>> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = RetrofitClient.apiService.getStickerPacks(
                accessToken = UserSession.accessToken!!,
                serverKey = com.worldmates.messenger.data.Constants.SERVER_KEY
            )

            if (response.apiStatus == 200 && response.packs != null) {
                _stickerPacks.value = response.packs
                cacheStickerPacks(response.packs)
                updateActiveStickers()
                Log.d("StickerRepository", "Завантажено ${response.packs.size} паків стікерів")
                Result.success(response.packs)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Не вдалося завантажити паки стікерів"))
            }
        } catch (e: Exception) {
            Log.e("StickerRepository", "Помилка завантаження паків стікерів", e)
            Result.failure(e)
        }
    }

    /**
     * Завантажує стікери з конкретного паку
     */
    suspend fun fetchStickerPack(packId: Long): Result<StickerPack> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = RetrofitClient.apiService.getStickerPack(
                accessToken = UserSession.accessToken!!,
                serverKey = com.worldmates.messenger.data.Constants.SERVER_KEY,
                packId = packId
            )

            if (response.apiStatus == 200 && response.pack != null) {
                updateStickerPackInList(response.pack)
                Result.success(response.pack)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Не вдалося завантажити пак стікерів"))
            }
        } catch (e: Exception) {
            Log.e("StickerRepository", "Помилка завантаження паку стікерів", e)
            Result.failure(e)
        }
    }

    /**
     * Активує пак стікерів
     */
    suspend fun activateStickerPack(packId: Long): Result<StickerPack> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = RetrofitClient.apiService.activateStickerPack(
                accessToken = UserSession.accessToken!!,
                packId = packId
            )

            if (response.apiStatus == 200 && response.pack != null) {
                updateStickerPackInList(response.pack)
                updateActiveStickers()
                Log.d("StickerRepository", "Активовано пак: ${response.pack.name}")
                Result.success(response.pack)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Не вдалося активувати пак"))
            }
        } catch (e: Exception) {
            Log.e("StickerRepository", "Помилка активації паку", e)
            Result.failure(e)
        }
    }

    /**
     * Деактивує пак стікерів
     */
    suspend fun deactivateStickerPack(packId: Long): Result<StickerPack> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = RetrofitClient.apiService.deactivateStickerPack(
                accessToken = UserSession.accessToken!!,
                packId = packId
            )

            if (response.apiStatus == 200 && response.pack != null) {
                updateStickerPackInList(response.pack)
                updateActiveStickers()
                Log.d("StickerRepository", "Деактивовано пак: ${response.pack.name}")
                Result.success(response.pack)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Не вдалося деактивувати пак"))
            }
        } catch (e: Exception) {
            Log.e("StickerRepository", "Помилка деактивації паку", e)
            Result.failure(e)
        }
    }

    /**
     * Отримує всі стікери з активних паків
     */
    fun getActiveStickers(): List<Sticker> {
        return _stickerPacks.value
            .filter { it.isActive }
            .flatMap { it.stickers ?: emptyList() }
    }

    /**
     * Отримує всі активні паки
     */
    fun getActivePacks(): List<StickerPack> {
        return _stickerPacks.value.filter { it.isActive }
    }

    /**
     * Оновлює список активних стікерів з активних паків
     */
    private fun updateActiveStickers() {
        _activeStickers.value = getActiveStickers()
        cacheActiveStickers(_activeStickers.value)
    }

    /**
     * Оновлює пак у списку
     */
    private fun updateStickerPackInList(updatedPack: StickerPack) {
        val currentPacks = _stickerPacks.value.toMutableList()
        val index = currentPacks.indexOfFirst { it.id == updatedPack.id }

        if (index != -1) {
            currentPacks[index] = updatedPack
        } else {
            currentPacks.add(updatedPack)
        }

        _stickerPacks.value = currentPacks
        cacheStickerPacks(currentPacks)
    }

    // ==================== КЕШУВАННЯ ====================

    private fun cacheStickerPacks(packs: List<StickerPack>) {
        prefs.edit().putString("sticker_packs", gson.toJson(packs)).apply()
    }

    private fun cacheActiveStickers(stickers: List<Sticker>) {
        prefs.edit().putString("active_stickers", gson.toJson(stickers)).apply()
    }

    private fun loadCachedStickers() {
        // Завантажуємо паки
        val packsJson = prefs.getString("sticker_packs", null)
        if (packsJson != null) {
            val type = object : TypeToken<List<StickerPack>>() {}.type
            _stickerPacks.value = gson.fromJson(packsJson, type)
        }

        // Завантажуємо активні стікери
        val stickersJson = prefs.getString("active_stickers", null)
        if (stickersJson != null) {
            val type = object : TypeToken<List<Sticker>>() {}.type
            _activeStickers.value = gson.fromJson(stickersJson, type)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: StickerRepository? = null

        fun getInstance(context: Context): StickerRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StickerRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
