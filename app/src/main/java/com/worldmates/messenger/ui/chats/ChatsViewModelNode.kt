package com.worldmates.messenger.ui.chats

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.model.Chat
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для екрана списку чатів через Node.js REST API.
 *
 * Окремий файл, щоб НЕ розширювати ChatsActivity.kt (~1400 рядків).
 *
 * Що обробляє:
 *   ✅ Завантаження списку особистих чатів
 *   ✅ Архівування / розархівування чату
 *   ✅ Заглушення / розглушення нотифікацій
 *   ✅ Пін / зняття піну чату у списку
 *   ✅ Зміна кольору чату
 *   ✅ Видалення всього діалогу
 *   ✅ Позначення чату прочитаним
 */
class ChatsViewModelNode(application: Application) : AndroidViewModel(application) {

    private val TAG = "ChatsViewModelNode"
    private val api = NodeRetrofitClient.api

    // ── State ─────────────────────────────────────────────────────────────────

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _actionResult = MutableStateFlow<ActionResult?>(null)
    val actionResult: StateFlow<ActionResult?> = _actionResult

    private var currentOffset = 0

    // ── Chats List ────────────────────────────────────────────────────────────

    fun loadChats(showArchived: Boolean = false, reset: Boolean = true) {
        if (reset) currentOffset = 0
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val resp = api.getChats(
                    limit        = 30,
                    offset       = currentOffset,
                    showArchived = if (showArchived) "true" else "false"
                )
                if (resp.apiStatus == 200) {
                    val data = resp.data ?: emptyList()
                    _chats.value = if (reset) data else _chats.value + data
                    currentOffset += data.size
                    Log.d(TAG, "Loaded ${data.size} chats (archived=$showArchived)")
                } else {
                    _error.value = resp.errorMessage ?: "Failed to load chats"
                    Log.w(TAG, "getChats failed: ${resp.errorMessage}")
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "loadChats error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMoreChats(showArchived: Boolean = false) {
        loadChats(showArchived, reset = false)
    }

    // ── Archive ────────────────────────────────────────────────────────────────

    fun archiveChat(chatId: Long, archive: Boolean) {
        viewModelScope.launch {
            try {
                val resp = api.archiveChat(chatId, if (archive) "yes" else "no")
                if (resp.apiStatus == 200) {
                    _chats.value = _chats.value.filter { it.userId?.toLong() != chatId }
                    _actionResult.value = ActionResult.Success(if (archive) "archived" else "unarchived")
                    Log.d(TAG, "Chat $chatId archived=$archive")
                } else {
                    _actionResult.value = ActionResult.Error(resp.errorMessage ?: "Archive failed")
                }
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: "Network error")
                Log.e(TAG, "archiveChat error", e)
            }
        }
    }

    // ── Mute ──────────────────────────────────────────────────────────────────

    fun muteChat(chatId: Long, mute: Boolean) {
        viewModelScope.launch {
            try {
                val resp = api.muteChat(
                    chatId    = chatId,
                    notify    = if (mute) "no" else "yes",
                    callChat  = if (mute) "no" else "yes"
                )
                if (resp.apiStatus == 200) {
                    _actionResult.value = ActionResult.Success(if (mute) "muted" else "unmuted")
                    Log.d(TAG, "Chat $chatId muted=$mute")
                } else {
                    _actionResult.value = ActionResult.Error(resp.errorMessage ?: "Mute failed")
                }
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: "Network error")
                Log.e(TAG, "muteChat error", e)
            }
        }
    }

    // ── Pin chat ──────────────────────────────────────────────────────────────

    fun pinChat(chatId: Long, pin: Boolean) {
        viewModelScope.launch {
            try {
                val resp = api.pinChat(chatId, if (pin) "yes" else "no")
                if (resp.apiStatus == 200) {
                    _actionResult.value = ActionResult.Success(if (pin) "pinned" else "unpinned")
                    Log.d(TAG, "Chat $chatId pinned=$pin")
                } else {
                    _actionResult.value = ActionResult.Error(resp.errorMessage ?: "Pin failed")
                }
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: "Network error")
                Log.e(TAG, "pinChat error", e)
            }
        }
    }

    // ── Color ─────────────────────────────────────────────────────────────────

    fun changeChatColor(userId: Long, color: String) {
        viewModelScope.launch {
            try {
                val hex = color.trimStart('#')
                val resp = api.changeChatColor(userId, hex)
                if (resp.apiStatus == 200) {
                    _actionResult.value = ActionResult.Success("color:#$hex")
                    Log.d(TAG, "Chat color changed to $hex for user $userId")
                } else {
                    _actionResult.value = ActionResult.Error(resp.errorMessage ?: "Color change failed")
                }
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: "Network error")
                Log.e(TAG, "changeChatColor error", e)
            }
        }
    }

    // ── Delete conversation ───────────────────────────────────────────────────

    fun deleteConversation(userId: Long, deleteType: String = "me") {
        viewModelScope.launch {
            try {
                val resp = api.deleteConversation(userId, deleteType)
                if (resp.apiStatus == 200) {
                    _chats.value = _chats.value.filter { it.userId?.toLong() != userId }
                    _actionResult.value = ActionResult.Success("conversation_deleted")
                    Log.d(TAG, "Conversation with $userId deleted ($deleteType)")
                } else {
                    _actionResult.value = ActionResult.Error(resp.errorMessage ?: "Delete failed")
                }
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: "Network error")
                Log.e(TAG, "deleteConversation error", e)
            }
        }
    }

    // ── Mark as read ──────────────────────────────────────────────────────────

    fun markChatAsRead(recipientId: Long) {
        viewModelScope.launch {
            try {
                api.readChat(recipientId)
                Log.d(TAG, "Chat with $recipientId marked as read")
            } catch (e: Exception) {
                Log.e(TAG, "markChatAsRead error", e)
            }
        }
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    fun clearActionResult() { _actionResult.value = null }
    fun clearError()        { _error.value = null }

    // ── Result types ──────────────────────────────────────────────────────────

    sealed class ActionResult {
        data class Success(val action: String) : ActionResult()
        data class Error(val reason: String)   : ActionResult()
    }
}
