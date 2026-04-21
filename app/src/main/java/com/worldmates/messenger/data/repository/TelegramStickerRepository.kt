package com.worldmates.messenger.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.TelegramStickerItem
import com.worldmates.messenger.network.TelegramStickerSetResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for Telegram sticker sets fetched via our server proxy.
 *
 * Flow:
 *   Android → GET /api/telegram/sticker-set/{setName}
 *   → Node.js calls TG Bot API (token stays server-side)
 *   → files cached on worldmates.club CDN
 *   → returns cached CDN URLs to Android
 *
 * Sticker sets are disk-cached in SharedPreferences so they load
 * instantly on next open without a network round-trip.
 */
class TelegramStickerRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tg_sticker_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val api  = NodeRetrofitClient.api

    private val _sets = MutableStateFlow<List<TelegramStickerSetResponse>>(emptyList())
    val sets: StateFlow<List<TelegramStickerSetResponse>> = _sets

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadCached()
    }

    /**
     * Fetch a sticker set by name (e.g. "HotCherry").
     * Result is merged into [sets] and persisted to disk.
     */
    suspend fun fetchSet(setName: String): Result<TelegramStickerSetResponse> {
        _isLoading.value = true
        return try {
            val response = api.getTelegramStickerSet(setName)
            if (response.apiStatus == 200 && response.stickers.isNotEmpty()) {
                mergeSet(response)
                Log.d(TAG, "Fetched TG set '${response.name}': ${response.stickers.size} stickers")
                Result.success(response)
            } else {
                Result.failure(Exception(response.errorMessage ?: "Empty sticker set"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch TG sticker set '$setName'", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    /** Fetch multiple sets (e.g. on StickerPicker open). */
    suspend fun fetchSets(setNames: List<String>) {
        setNames.forEach { fetchSet(it) }
    }

    /** All stickers across all loaded sets, flat-mapped. */
    fun getAllStickers(): List<TelegramStickerItem> =
        _sets.value.flatMap { it.stickers }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun mergeSet(set: TelegramStickerSetResponse) {
        val current = _sets.value.toMutableList()
        val idx = current.indexOfFirst { it.name == set.name }
        if (idx != -1) current[idx] = set else current.add(set)
        _sets.value = current
        persist(current)
    }

    private fun persist(sets: List<TelegramStickerSetResponse>) {
        prefs.edit().putString(KEY_SETS, gson.toJson(sets)).apply()
    }

    private fun loadCached() {
        val json = prefs.getString(KEY_SETS, null) ?: return
        val type = object : TypeToken<List<TelegramStickerSetResponse>>() {}.type
        _sets.value = gson.fromJson(json, type)
    }

    companion object {
        private const val TAG = "TelegramStickerRepo"
        private const val KEY_SETS = "tg_sticker_sets"

        @Volatile private var INSTANCE: TelegramStickerRepository? = null

        fun getInstance(context: Context): TelegramStickerRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TelegramStickerRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
