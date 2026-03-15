package com.worldmates.messenger.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local storage for saved (bookmarked) messages.
 * Stored per-user in SharedPreferences, consistent with ChatOrganizationManager.
 */
object SavedMessagesManager {

    private fun prefsName(userId: Long) = "saved_messages_$userId"
    private const val KEY_SAVED = "saved_v1"

    private val gson = Gson()

    private val _savedMessages = MutableStateFlow<List<SavedMessageItem>>(emptyList())
    val savedMessages: StateFlow<List<SavedMessageItem>> = _savedMessages.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val userId = UserSession.userId
        prefs = context.getSharedPreferences(prefsName(userId), Context.MODE_PRIVATE)
        load()
    }

    fun reinitForAccount(context: Context, userId: Long) {
        prefs = context.getSharedPreferences(prefsName(userId), Context.MODE_PRIVATE)
        load()
    }

    private fun load() {
        val json = prefs?.getString(KEY_SAVED, null) ?: return
        val type = object : TypeToken<List<SavedMessageItem>>() {}.type
        _savedMessages.value = gson.fromJson(json, type) ?: emptyList()
    }

    private fun save() {
        prefs?.edit()?.putString(KEY_SAVED, gson.toJson(_savedMessages.value))?.apply()
    }

    fun saveMessage(item: SavedMessageItem) {
        if (_savedMessages.value.any { it.messageId == item.messageId && it.chatType == item.chatType }) return
        _savedMessages.value = listOf(item) + _savedMessages.value
        save()
    }

    fun removeSaved(messageId: Long, chatType: String) {
        _savedMessages.value = _savedMessages.value.filter {
            !(it.messageId == messageId && it.chatType == chatType)
        }
        save()
    }

    fun isSaved(messageId: Long, chatType: String): Boolean =
        _savedMessages.value.any { it.messageId == messageId && it.chatType == chatType }

    fun clearAll() {
        _savedMessages.value = emptyList()
        save()
    }
}

/**
 * A single saved message entry.
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
