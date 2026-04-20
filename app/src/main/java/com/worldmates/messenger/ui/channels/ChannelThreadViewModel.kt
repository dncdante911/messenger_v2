package com.worldmates.messenger.ui.channels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.ThreadMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChannelThreadViewModel : ViewModel() {

    private val _messages   = MutableStateFlow<List<ThreadMessage>>(emptyList())
    val messages: StateFlow<List<ThreadMessage>> = _messages

    private val _isLoading  = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error      = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _total      = MutableStateFlow(0)
    val total: StateFlow<Int> = _total

    private var currentPostId: Long = 0L

    fun loadThread(postId: Long, offset: Int = 0, limit: Int = 50) {
        currentPostId = postId
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val resp = NodeRetrofitClient.api.getThreadMessages(postId, limit, offset)
                if (resp.apiStatus == 200) {
                    if (offset == 0) {
                        _messages.value = resp.messages ?: emptyList()
                    } else {
                        _messages.value = _messages.value + (resp.messages ?: emptyList())
                    }
                    _total.value = resp.total
                } else {
                    _error.value = resp.errorMessage ?: "Помилка завантаження"
                }
            } catch (e: Exception) {
                Log.e("ChannelThread", "loadThread error", e)
                _error.value = e.localizedMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendMessage(
        postId: Long,
        text: String,
        replyToId: Long? = null,
        sticker: String? = null,
        onSent: () -> Unit = {}
    ) {
        if (text.isBlank() && sticker.isNullOrBlank()) return
        viewModelScope.launch {
            try {
                val resp = NodeRetrofitClient.api.sendThreadMessage(postId, text.trim(), replyToId, sticker)
                if (resp.apiStatus == 200 && resp.message != null) {
                    _messages.value = _messages.value + resp.message
                    _total.value    = _total.value + 1
                    onSent()
                } else {
                    _error.value = resp.errorMessage ?: "Не вдалося надіслати"
                }
            } catch (e: Exception) {
                Log.e("ChannelThread", "sendMessage error", e)
                _error.value = e.localizedMessage
            }
        }
    }

    fun deleteMessage(
        postId: Long,
        msgId: Long,
        onDeleted: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val resp = NodeRetrofitClient.api.deleteThreadMessage(postId, msgId)
                if (resp.apiStatus == 200) {
                    _messages.value = _messages.value.filter { it.id != msgId }
                    _total.value    = maxOf(0, _total.value - 1)
                    onDeleted()
                } else {
                    _error.value = resp.errorMessage ?: "Не вдалося видалити"
                }
            } catch (e: Exception) {
                Log.e("ChannelThread", "deleteMessage error", e)
                _error.value = e.localizedMessage
            }
        }
    }

    /** Called from Socket.IO: new thread message arrived */
    fun onSocketThreadMessage(msg: ThreadMessage) {
        if (msg.postId == currentPostId && _messages.value.none { it.id == msg.id }) {
            _messages.value = _messages.value + msg
            _total.value    = _total.value + 1
        }
    }

    /** Called from Socket.IO: thread message deleted */
    fun onSocketThreadDeleted(postId: Long, msgId: Long) {
        if (postId == currentPostId) {
            _messages.value = _messages.value.filter { it.id != msgId }
        }
    }

    fun clearError() { _error.value = null }
}
