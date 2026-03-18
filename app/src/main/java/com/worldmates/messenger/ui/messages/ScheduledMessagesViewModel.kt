package com.worldmates.messenger.ui.messages

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.data.model.ScheduledPost
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.network.ScheduledMessageItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScheduledMessagesViewModel : ViewModel() {

    companion object {
        private const val TAG = "ScheduledMsgVM"
    }

    private var chatId:   Long   = 0L
    private var chatType: String = "dm"

    private val _posts     = MutableStateFlow<List<ScheduledPost>>(emptyList())
    val posts: StateFlow<List<ScheduledPost>> = _posts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun init(chatId: Long, chatType: String) {
        this.chatId   = chatId
        this.chatType = chatType
        loadPosts()
    }

    fun loadPosts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = NodeRetrofitClient.api.getScheduledMessages(
                    chatId   = chatId,
                    chatType = chatType
                )
                if (response.apiStatus == 200) {
                    _posts.value = response.scheduled
                        ?.map { mapItem(it) }
                        ?: emptyList()
                    Log.d(TAG, "Loaded ${_posts.value.size} scheduled messages for $chatType $chatId")
                } else {
                    _posts.value = emptyList()
                    _errorMessage.value = response.errorMessage
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading scheduled messages", e)
                _posts.value = emptyList()
                _errorMessage.value = e.localizedMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createPost(
        text:          String,
        scheduledTime: Long,
        mediaUrl:      String?  = null,
        repeatType:    String   = "none",
        isPinned:      Boolean  = false,
        notifyMembers: Boolean  = true,
        onSuccess:     () -> Unit = {},
        onError:       (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.api.createScheduledMessage(
                    chatId        = chatId,
                    chatType      = chatType,
                    text          = text,
                    scheduledAt   = scheduledTime / 1000L,
                    repeatType    = repeatType,
                    isPinned      = isPinned,
                    notifyMembers = notifyMembers
                )
                if (response.apiStatus == 200) {
                    loadPosts()
                    onSuccess()
                } else {
                    onError(response.errorMessage ?: "Error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating scheduled message", e)
                onError(e.localizedMessage ?: "Error")
            }
        }
    }

    fun updatePost(
        post:          ScheduledPost,
        text:          String,
        scheduledTime: Long,
        repeatType:    String  = "none",
        isPinned:      Boolean = false,
        notifyMembers: Boolean = true,
        onSuccess:     () -> Unit = {},
        onError:       (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.api.updateScheduledMessage(
                    id          = post.id,
                    text        = text,
                    scheduledAt = scheduledTime / 1000L,
                    repeatType  = repeatType
                )
                if (response.apiStatus == 200) {
                    loadPosts()
                    onSuccess()
                } else {
                    onError(response.errorMessage ?: "Error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating scheduled message", e)
                onError(e.localizedMessage ?: "Error")
            }
        }
    }

    fun deletePost(
        post:      ScheduledPost,
        onSuccess: () -> Unit = {},
        onError:   (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.api.deleteScheduledMessage(id = post.id)
                if (response.apiStatus == 200) {
                    _posts.value = _posts.value.filter { it.id != post.id }
                    onSuccess()
                } else {
                    onError(response.errorMessage ?: "Error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting scheduled message", e)
                onError(e.localizedMessage ?: "Error")
            }
        }
    }

    fun publishNow(
        post:      ScheduledPost,
        onSuccess: () -> Unit = {},
        onError:   (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val response = NodeRetrofitClient.api.sendScheduledNow(id = post.id)
                if (response.apiStatus == 200) {
                    _posts.value = _posts.value.filter { it.id != post.id }
                    onSuccess()
                } else {
                    onError(response.errorMessage ?: "Error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error publishing scheduled message now", e)
                onError(e.localizedMessage ?: "Error")
            }
        }
    }

    private fun mapItem(item: ScheduledMessageItem): ScheduledPost = ScheduledPost(
        id            = item.id,
        groupId       = if (chatType == "group") chatId else null,
        channelId     = if (chatType == "channel") chatId else null,
        authorId      = 0L,
        text          = item.text ?: "",
        mediaUrl      = item.mediaUrl,
        mediaType     = item.mediaType,
        scheduledTime = item.scheduledAt * 1000L,
        createdTime   = item.createdAt * 1000L,
        status        = if (item.status == "pending") "scheduled" else item.status,
        repeatType    = item.repeatType,
        isPinned      = item.isPinned,
        notifyMembers = item.notifyMembers
    )
}
