package com.worldmates.messenger.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.worldmates.messenger.data.model.CustomEmoji
import com.worldmates.messenger.data.model.EmojiPack
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Репозиторій для роботи з кастомними емоджі
 */
class EmojiRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("emoji_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _customEmojis = MutableStateFlow<List<CustomEmoji>>(emptyList())
    val customEmojis: StateFlow<List<CustomEmoji>> = _customEmojis

    private val _emojiPacks = MutableStateFlow<List<EmojiPack>>(emptyList())
    val emojiPacks: StateFlow<List<EmojiPack>> = _emojiPacks

    init {
        // Завантажуємо кешовані емоджі при ініціалізації
        loadCachedEmojis()
    }

    /**
     * Завантажує список паків емоджі з API
     */
    suspend fun fetchEmojiPacks(): Result<List<EmojiPack>> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = RetrofitClient.apiService.getEmojiPacks(
                accessToken = UserSession.accessToken!!
            )

            if (response.apiStatus == 200 && response.packs != null) {
                _emojiPacks.value = response.packs
                cacheEmojiPacks(response.packs)
                Log.d("EmojiRepository", "Завантажено ${response.packs.size} паків емоджі")
                Result.success(response.packs)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Не вдалося завантажити паки емоджі"))
            }
        } catch (e: Exception) {
            Log.e("EmojiRepository", "Помилка завантаження паків емоджі", e)
            Result.failure(e)
        }
    }

    /**
     * Завантажує емоджі з конкретного паку
     */
    suspend fun fetchEmojiPack(packId: Long): Result<EmojiPack> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = RetrofitClient.apiService.getEmojiPack(
                accessToken = UserSession.accessToken!!,
                packId = packId
            )

            if (response.apiStatus == 200 && response.pack != null) {
                updateEmojiPackInList(response.pack)
                Result.success(response.pack)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Не вдалося завантажити пак емоджі"))
            }
        } catch (e: Exception) {
            Log.e("EmojiRepository", "Помилка завантаження паку емоджі", e)
            Result.failure(e)
        }
    }

    /**
     * Активує пак емоджі
     */
    suspend fun activateEmojiPack(packId: Long): Result<EmojiPack> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = RetrofitClient.apiService.activateEmojiPack(
                accessToken = UserSession.accessToken!!,
                packId = packId
            )

            if (response.apiStatus == 200 && response.pack != null) {
                updateEmojiPackInList(response.pack)
                updateCustomEmojis()
                Log.d("EmojiRepository", "Активовано пак: ${response.pack.name}")
                Result.success(response.pack)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Не вдалося активувати пак"))
            }
        } catch (e: Exception) {
            Log.e("EmojiRepository", "Помилка активації паку", e)
            Result.failure(e)
        }
    }

    /**
     * Деактивує пак емоджі
     */
    suspend fun deactivateEmojiPack(packId: Long): Result<EmojiPack> {
        return try {
            if (UserSession.accessToken == null) {
                return Result.failure(Exception("Не авторизовано"))
            }

            val response = RetrofitClient.apiService.deactivateEmojiPack(
                accessToken = UserSession.accessToken!!,
                packId = packId
            )

            if (response.apiStatus == 200 && response.pack != null) {
                updateEmojiPackInList(response.pack)
                updateCustomEmojis()
                Log.d("EmojiRepository", "Деактивовано пак: ${response.pack.name}")
                Result.success(response.pack)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Не вдалося деактивувати пак"))
            }
        } catch (e: Exception) {
            Log.e("EmojiRepository", "Помилка деактивації паку", e)
            Result.failure(e)
        }
    }

    /**
     * Отримує всі активні кастомні емоджі
     */
    fun getActiveCustomEmojis(): List<CustomEmoji> {
        return _emojiPacks.value
            .filter { it.isActive }
            .flatMap { it.emojis ?: emptyList() }
    }

    /**
     * Оновлює список кастомних емоджі з активних паків
     */
    private fun updateCustomEmojis() {
        _customEmojis.value = getActiveCustomEmojis()
        cacheCustomEmojis(_customEmojis.value)
    }

    /**
     * Оновлює пак у списку
     */
    private fun updateEmojiPackInList(updatedPack: EmojiPack) {
        val currentPacks = _emojiPacks.value.toMutableList()
        val index = currentPacks.indexOfFirst { it.id == updatedPack.id }

        if (index != -1) {
            currentPacks[index] = updatedPack
        } else {
            currentPacks.add(updatedPack)
        }

        _emojiPacks.value = currentPacks
        cacheEmojiPacks(currentPacks)
    }

    // ==================== КЕШУВАННЯ ====================

    private fun cacheEmojiPacks(packs: List<EmojiPack>) {
        prefs.edit().putString("emoji_packs", gson.toJson(packs)).apply()
    }

    private fun cacheCustomEmojis(emojis: List<CustomEmoji>) {
        prefs.edit().putString("custom_emojis", gson.toJson(emojis)).apply()
    }

    private fun loadCachedEmojis() {
        // Завантажуємо паки
        val packsJson = prefs.getString("emoji_packs", null)
        if (packsJson != null) {
            val type = object : TypeToken<List<EmojiPack>>() {}.type
            _emojiPacks.value = gson.fromJson(packsJson, type)
        }

        // Завантажуємо кастомні емоджі
        val emojisJson = prefs.getString("custom_emojis", null)
        if (emojisJson != null) {
            val type = object : TypeToken<List<CustomEmoji>>() {}.type
            _customEmojis.value = gson.fromJson(emojisJson, type)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: EmojiRepository? = null

        fun getInstance(context: Context): EmojiRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EmojiRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
