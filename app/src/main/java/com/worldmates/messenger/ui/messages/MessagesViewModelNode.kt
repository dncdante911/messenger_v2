package com.worldmates.messenger.ui.messages

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.Constants
import com.worldmates.messenger.data.UserSession
import com.worldmates.messenger.data.model.Chat
import com.worldmates.messenger.data.model.Message
import com.worldmates.messenger.network.NodeRetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для особистих чатів через Node.js REST API.
 *
 * Окремий файл щоб НЕ розширювати MessagesViewModel.kt (він вже ~1850 рядків).
 * Використовується поруч з MessagesViewModel або як його заміна у приватних чатах.
 *
 * Що обробляє цей файл (Node.js шлях):
 *   ✅ Завантаження повідомлень (get / loadmore)
 *   ✅ Відправка текстових повідомлень
 *   ✅ Редагування повідомлення
 *   ✅ Видалення повідомлення
 *   ✅ Реакції на повідомлення
 *   ✅ Пін / зняття піну повідомлення
 *   ✅ Пересилання повідомлень
 *   ✅ Пошук у чаті
 *   ✅ Відмітка "прочитано"
 *   ✅ Індикатор набору тексту
 *   ✅ Улюблені повідомлення
 *
 * Що лишається на PHP (не критично для швидкості):
 *   - Завантаження медіафайлів (upload) — через PHP send-message.php + file
 *   - Push-нотифікації FCM
 */
class MessagesViewModelNode(application: Application) : AndroidViewModel(application) {

    private val TAG = "MessagesViewModelNode"
    private val api = NodeRetrofitClient.api

    // ── State ─────────────────────────────────────────────────────────────────

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _sendResult = MutableStateFlow<SendResult?>(null)
    val sendResult: StateFlow<SendResult?> = _sendResult

    private val _searchResults = MutableStateFlow<List<Message>>(emptyList())
    val searchResults: StateFlow<List<Message>> = _searchResults

    private val _pinnedMessages = MutableStateFlow<List<Message>>(emptyList())
    val pinnedMessages: StateFlow<List<Message>> = _pinnedMessages

    private val _favMessages = MutableStateFlow<List<Message>>(emptyList())
    val favMessages: StateFlow<List<Message>> = _favMessages

    private val _actionResult = MutableStateFlow<ActionResult?>(null)
    val actionResult: StateFlow<ActionResult?> = _actionResult

    // current conversation partner
    private var recipientId: Long = 0
    private var typingJob: Job? = null

    // ── Initialization ────────────────────────────────────────────────────────

    fun initialize(recipientId: Long) {
        this.recipientId = recipientId
        fetchMessages()
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    fun fetchMessages(beforeMessageId: Long = 0) {
        if (recipientId == 0L) return
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val resp = api.getMessages(
                    recipientId   = recipientId,
                    limit         = Constants.MESSAGES_PAGE_SIZE,
                    beforeMessageId = beforeMessageId
                )

                if (resp.apiStatus == 200 && resp.messages != null) {
                    if (beforeMessageId == 0L) {
                        _messages.value = resp.messages
                    } else {
                        // prepend older messages
                        _messages.value = resp.messages + _messages.value
                    }
                    Log.d(TAG, "Loaded ${resp.messages.size} messages")
                } else {
                    _error.value = resp.errorMessage ?: "Failed to load messages"
                    Log.w(TAG, "getMessages failed: ${resp.errorMessage}")
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "getMessages error", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        val oldest = _messages.value.firstOrNull()?.id ?: return
        if (_isLoadingMore.value) return
        _isLoadingMore.value = true

        viewModelScope.launch {
            try {
                val resp = api.loadMore(
                    recipientId      = recipientId,
                    beforeMessageId  = oldest,
                    limit            = 15
                )
                if (resp.apiStatus == 200 && !resp.messages.isNullOrEmpty()) {
                    _messages.value = resp.messages + _messages.value
                    Log.d(TAG, "LoadMore: +${resp.messages.size} messages")
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMore error", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    fun sendMessage(text: String, replyToId: Long? = null) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                val resp = api.sendMessage(
                    recipientId = recipientId,
                    text        = text,
                    replyId     = replyToId
                )
                if (resp.apiStatus == 200 && resp.messageData != null) {
                    // append optimistically if Socket.IO doesn't push it back fast enough
                    val exists = _messages.value.any { it.id == resp.messageData.id }
                    if (!exists) {
                        _messages.value = _messages.value + resp.messageData
                    }
                    _sendResult.value = SendResult.Success(resp.messageData)
                    Log.d(TAG, "Message sent id=${resp.messageData.id}")
                } else {
                    _sendResult.value = SendResult.Error(resp.errorMessage ?: "Send failed")
                }
            } catch (e: Exception) {
                _sendResult.value = SendResult.Error(e.message ?: "Network error")
                Log.e(TAG, "sendMessage error", e)
            }
        }
    }

    fun clearSendResult() { _sendResult.value = null }

    // ── Edit ──────────────────────────────────────────────────────────────────

    fun editMessage(messageId: Long, newText: String) {
        viewModelScope.launch {
            try {
                val resp = api.editMessage(messageId, newText)
                if (resp.apiStatus == 200) {
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == messageId) msg.copy(text = newText) else msg
                    }
                    _actionResult.value = ActionResult.Success("edited")
                    Log.d(TAG, "Message $messageId edited")
                } else {
                    _actionResult.value = ActionResult.Error(resp.errorMessage ?: "Edit failed")
                }
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: "Network error")
                Log.e(TAG, "editMessage error", e)
            }
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun deleteMessage(messageId: Long, deleteType: String = "just_me") {
        viewModelScope.launch {
            try {
                val resp = api.deleteMessage(messageId, deleteType)
                if (resp.apiStatus == 200) {
                    _messages.value = _messages.value.filter { it.id != messageId }
                    _actionResult.value = ActionResult.Success("deleted")
                    Log.d(TAG, "Message $messageId deleted ($deleteType)")
                } else {
                    _actionResult.value = ActionResult.Error(resp.errorMessage ?: "Delete failed")
                }
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: "Network error")
                Log.e(TAG, "deleteMessage error", e)
            }
        }
    }

    // ── React ─────────────────────────────────────────────────────────────────

    fun reactToMessage(messageId: Long, reaction: String) {
        viewModelScope.launch {
            try {
                val resp = api.reactToMessage(messageId, reaction)
                if (resp.apiStatus == 200) {
                    _actionResult.value = ActionResult.Success("react:${resp.action}")
                    Log.d(TAG, "Reaction ${resp.action}: $reaction on msg $messageId")
                } else {
                    _actionResult.value = ActionResult.Error(resp.errorMessage ?: "React failed")
                }
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: "Network error")
                Log.e(TAG, "reactToMessage error", e)
            }
        }
    }

    // ── Pin message ───────────────────────────────────────────────────────────

    fun pinMessage(messageId: Long, pin: Boolean = true) {
        viewModelScope.launch {
            try {
                val resp = api.pinMessage(
                    messageId = messageId,
                    chatId    = recipientId,
                    pin       = if (pin) "yes" else "no"
                )
                if (resp.apiStatus == 200) {
                    _actionResult.value = ActionResult.Success(if (pin) "pinned" else "unpinned")
                    // refresh pinned list
                    fetchPinnedMessages()
                } else {
                    _actionResult.value = ActionResult.Error(resp.errorMessage ?: "Pin failed")
                }
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: "Network error")
                Log.e(TAG, "pinMessage error", e)
            }
        }
    }

    fun fetchPinnedMessages() {
        viewModelScope.launch {
            try {
                val resp = api.getPinnedMessages(recipientId)
                if (resp.apiStatus == 200) {
                    _pinnedMessages.value = resp.messages ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchPinnedMessages error", e)
            }
        }
    }

    // ── Forward ───────────────────────────────────────────────────────────────

    fun forwardMessages(messageIds: Set<Long>, toRecipientIds: List<Long>) {
        if (messageIds.isEmpty() || toRecipientIds.isEmpty()) return
        viewModelScope.launch {
            try {
                val idsCsv = toRecipientIds.joinToString(",")
                var allOk  = true
                for (msgId in messageIds) {
                    val resp = api.forwardMessage(msgId, idsCsv)
                    if (resp.apiStatus != 200) {
                        allOk = false
                        Log.w(TAG, "Forward msg $msgId failed: ${resp.errorMessage}")
                    }
                }
                _actionResult.value = if (allOk)
                    ActionResult.Success("forwarded")
                else
                    ActionResult.Error("Some messages could not be forwarded")
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: "Network error")
                Log.e(TAG, "forwardMessages error", e)
            }
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun searchMessages(query: String) {
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val resp = api.searchMessages(
                    recipientId = recipientId,
                    query       = query,
                    limit       = 50
                )
                _searchResults.value = resp.messages ?: emptyList()
                Log.d(TAG, "Search '$query': ${resp.messages?.size ?: 0} results")
            } catch (e: Exception) {
                Log.e(TAG, "searchMessages error", e)
                _searchResults.value = emptyList()
            }
        }
    }

    fun clearSearch() { _searchResults.value = emptyList() }

    // ── Seen ──────────────────────────────────────────────────────────────────

    fun markSeen() {
        if (recipientId == 0L) return
        viewModelScope.launch {
            try {
                api.markSeen(recipientId)
                Log.d(TAG, "Marked seen from $recipientId")
            } catch (e: Exception) {
                Log.e(TAG, "markSeen error", e)
            }
        }
    }

    // ── Typing ────────────────────────────────────────────────────────────────

    fun sendTypingStart() {
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            try {
                api.sendTyping(recipientId, "true")
                delay(5000)
                api.sendTyping(recipientId, "false")
            } catch (e: Exception) {
                Log.e(TAG, "typing error", e)
            }
        }
    }

    fun sendTypingStop() {
        typingJob?.cancel()
        viewModelScope.launch {
            try { api.sendTyping(recipientId, "false") }
            catch (e: Exception) { Log.e(TAG, "typing stop error", e) }
        }
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    fun favMessage(messageId: Long, fav: Boolean) {
        viewModelScope.launch {
            try {
                val resp = api.favMessage(
                    messageId = messageId,
                    chatId    = recipientId,
                    fav       = if (fav) "yes" else "no"
                )
                if (resp.apiStatus == 200) {
                    _actionResult.value = ActionResult.Success(if (fav) "fav_added" else "fav_removed")
                    if (fav) fetchFavMessages()
                } else {
                    _actionResult.value = ActionResult.Error(resp.errorMessage ?: "Fav failed")
                }
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: "Network error")
                Log.e(TAG, "favMessage error", e)
            }
        }
    }

    fun fetchFavMessages() {
        viewModelScope.launch {
            try {
                val resp = api.getFavMessages(recipientId)
                if (resp.apiStatus == 200) {
                    _favMessages.value = resp.messages ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchFavMessages error", e)
            }
        }
    }

    // ── Clear action result ───────────────────────────────────────────────────

    fun clearActionResult() { _actionResult.value = null }

    // ── Result types ──────────────────────────────────────────────────────────

    sealed class SendResult {
        data class Success(val message: Message) : SendResult()
        data class Error(val reason: String)     : SendResult()
    }

    sealed class ActionResult {
        data class Success(val action: String) : ActionResult()
        data class Error(val reason: String)   : ActionResult()
    }
}
