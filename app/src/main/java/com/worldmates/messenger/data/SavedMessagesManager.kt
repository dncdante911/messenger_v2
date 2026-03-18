package com.worldmates.messenger.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.ServerSavedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SavedMessagesManager {

    private fun prefsName(userId: Long) = "saved_messages_$userId"
    private const val KEY_SAVED = "saved_v2"

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _savedMessages = MutableStateFlow<List<SavedMessageItem>>(emptyList())
    val savedMessages: StateFlow<List<SavedMessageItem>> = _savedMessages.asStateFlow()

    private var prefs: SharedPreferences? = null

    // ── init ──────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        val userId = UserSession.userId
        prefs = context.getSharedPreferences(prefsName(userId), Context.MODE_PRIVATE)
        loadLocal()
        syncFromServer()
    }

    fun reinitForAccount(context: Context, userId: Long) {
        prefs = context.getSharedPreferences(prefsName(userId), Context.MODE_PRIVATE)
        loadLocal()
        syncFromServer()
    }

    // ── local cache ───────────────────────────────────────────────────────────

    private fun loadLocal() {
        val json = prefs?.getString(KEY_SAVED, null) ?: return
        val type = object : TypeToken<List<SavedMessageItem>>() {}.type
        _savedMessages.value = gson.fromJson(json, type) ?: emptyList()
    }

    private fun persistLocal() {
        prefs?.edit()?.putString(KEY_SAVED, gson.toJson(_savedMessages.value))?.apply()
    }

    // ── server sync ───────────────────────────────────────────────────────────

    private fun syncFromServer() {
        scope.launch {
            try {
                val resp = NodeRetrofitClient.api.listSaved()
                if (resp.apiStatus == 200 && resp.saved != null) {
                    val items = resp.saved.map { it.toLocal() }
                    _savedMessages.value = items
                    persistLocal()
                }
            } catch (_: Exception) {
                // keep cached data on network failure
            }
        }
    }

    // ── public API ────────────────────────────────────────────────────────────

    fun saveMessage(item: SavedMessageItem) {
        // optimistic local update
        if (_savedMessages.value.any { it.messageId == item.messageId && it.chatType == item.chatType }) return
        _savedMessages.value = listOf(item) + _savedMessages.value
        persistLocal()

        scope.launch {
            try {
                NodeRetrofitClient.api.saveMessage(
                    messageId    = item.messageId,
                    chatType     = item.chatType,
                    chatId       = item.chatId,
                    chatName     = item.chatName,
                    senderName   = item.senderName,
                    text         = item.text.takeIf { it.isNotBlank() },
                    mediaUrl     = item.mediaUrl,
                    mediaType    = item.mediaType,
                    originalTime = item.originalTime,
                )
            } catch (_: Exception) {
                // local state already updated; will re-sync on next init
            }
        }
    }

    fun removeSaved(messageId: Long, chatType: String) {
        // optimistic local update
        _savedMessages.value = _savedMessages.value.filter {
            !(it.messageId == messageId && it.chatType == chatType)
        }
        persistLocal()

        scope.launch {
            try {
                NodeRetrofitClient.api.unsaveMessage(messageId = messageId, chatType = chatType)
            } catch (_: Exception) {
                // local state already updated
            }
        }
    }

    fun isSaved(messageId: Long, chatType: String): Boolean =
        _savedMessages.value.any { it.messageId == messageId && it.chatType == chatType }

    fun clearAll() {
        _savedMessages.value = emptyList()
        persistLocal()

        scope.launch {
            try {
                NodeRetrofitClient.api.clearSaved()
            } catch (_: Exception) {
                // local state already updated
            }
        }
    }
}

/**
 * A single saved message entry (local representation).
 */
data class SavedMessageItem(
    val messageId: Long,
    val chatType: String,       // "chat" | "group" | "channel"
    val chatId: Long,
    val chatName: String,
    val senderName: String,
    val text: String,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val savedAt: Long = System.currentTimeMillis() / 1000L,
    val originalTime: Long = 0L
)

// ── mapper ────────────────────────────────────────────────────────────────────

fun ServerSavedItem.toLocal() = SavedMessageItem(
    messageId    = messageId,
    chatType     = chatType,
    chatId       = chatId,
    chatName     = chatName,
    senderName   = senderName,
    text         = text ?: "",
    mediaUrl     = mediaUrl,
    mediaType    = mediaType,
    savedAt      = savedAt,
    originalTime = originalTime,
)